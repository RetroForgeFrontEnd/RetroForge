// RetroAchievements for disc games kept as CHD: a CD reader for rcheevos' hashing that reads CHD files through
// libchdr, and hands every other disc (cue/bin, gdi, iso) to rcheevos' own reader.

#include "cheevos_chd.h"

#include <libchdr/cdrom.h>
#include <libchdr/chd.h>
#include <rc_hash.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <strings.h>
#include <vector>

namespace {

struct Track {
    int number = 0;
    bool audio = false;
    int dataOffset = 0;    // bytes from the start of a stored frame to the sector's user data
    int dataSize = 2048;   // user data per sector that rcheevos reads (2048, or 2352 for audio)
    uint32_t frames = 0;   // frames of the track's own data (a stored pregap left out)
    uint32_t chdFrame = 0; // the track's first data frame in the CHD
    uint32_t lba = 0;      // that frame's address on the disc
};

struct ChdTrack {
    FILE *file = nullptr;
    chd_file *chd = nullptr;
    uint32_t hunkBytes = 0, unitBytes = 0, cachedHunk = UINT32_MAX;
    std::vector<uint8_t> hunk;
    Track track;
    ~ChdTrack() {
        if (chd) chd_close(chd);
        if (file) fclose(file);
    }

    // The stored frame [frame], or null when it can't be read.
    const uint8_t *frame(uint32_t frame) {
        uint32_t perHunk = hunkBytes / unitBytes;
        uint32_t index = frame / perHunk;
        if (index != cachedHunk) {
            if (chd_read(chd, index, hunk.data()) != CHDERR_NONE) return nullptr;
            cachedHunk = index;
        }
        return hunk.data() + static_cast<size_t>(frame % perHunk) * unitBytes;
    }
};

// A track handle given to rcheevos: ours for a CHD, else one from rcheevos' own reader.
struct Handle {
    std::unique_ptr<ChdTrack> chd;
    void *other = nullptr;
};

rc_hash_cdreader_t defaultReader{};
FILE *(*openFile)(const char *path) = nullptr;

FILE *openPath(const char *path) { return openFile ? openFile(path) : fopen(path, "rb"); }

bool isChd(const char *path) {
    const char *dot = path ? strrchr(path, '.') : nullptr;
    return dot && strcasecmp(dot, ".chd") == 0;
}

// Where a sector's user data starts in a stored frame, and how much rcheevos reads, for each CHD track type.
void layout(const std::string &type, Track &t) {
    t.audio = type == "AUDIO";
    t.dataSize = t.audio ? 2352 : 2048;
    if (type == "MODE1_RAW") t.dataOffset = 16;
    else if (type == "MODE2_RAW") t.dataOffset = 24;
    else if (type == "MODE2" || type == "MODE2_FORM_MIX") t.dataOffset = 8;
    else t.dataOffset = 0;  // MODE1, MODE2_FORM1, MODE2_FORM2 and AUDIO are stored without a header
}

// Every track on a CHD disc from its CD or GD-ROM metadata, with where each starts in the file and on the disc.
std::vector<Track> readTracks(chd_file *chd) {
    std::vector<Track> tracks;
    char meta[256];
    uint32_t len = 0, tag = 0;
    uint8_t flags = 0;
    uint32_t fileFrame = 0, lba = 0;
    auto get = [&](uint32_t want, uint32_t i) {
        if (chd_get_metadata(chd, want, i, meta, sizeof(meta) - 1, &len, &tag, &flags) != CHDERR_NONE) return false;
        meta[std::min<uint32_t>(len, sizeof(meta) - 1)] = 0;
        return true;
    };
    for (uint32_t i = 0;; i++) {
        Track t{};
        char type[32] = {}, subtype[32] = {}, pgtype[32] = {}, pgsub[32] = {};
        int frames = 0, pregap = 0, postgap = 0, pad = 0;
        bool gd = false;
        if (get(CDROM_TRACK_METADATA2_TAG, i)) {
            if (sscanf(meta, CDROM_TRACK_METADATA2_FORMAT, &t.number, type, subtype, &frames, &pregap, pgtype, pgsub, &postgap) < 4) break;
        } else if (get(GDROM_TRACK_METADATA_TAG, i)) {
            if (sscanf(meta, GDROM_TRACK_METADATA_FORMAT, &t.number, type, subtype, &frames, &pad, &pregap, pgtype, pgsub, &postgap) < 4) break;
            gd = true;
        } else if (get(CDROM_TRACK_METADATA_TAG, i)) {
            if (sscanf(meta, CDROM_TRACK_METADATA_FORMAT, &t.number, type, subtype, &frames) < 4) break;
        } else {
            break;
        }
        layout(type, t);
        // A pregap is in the file only when its type starts with V; either way it comes before the track on the disc.
        uint32_t stored = pgtype[0] == 'V' ? static_cast<uint32_t>(pregap) : 0;
        t.frames = static_cast<uint32_t>(frames) - std::min(stored, static_cast<uint32_t>(frames));
        t.chdFrame = fileFrame + stored;
        if (gd && t.number == 3) lba = 45000;  // a GD-ROM's high-density area
        t.lba = t.number == 1 ? 0 : lba + static_cast<uint32_t>(pregap);
        lba = t.lba + t.frames + static_cast<uint32_t>(postgap);
        // Each track is padded in the file to a multiple of 4 frames (a GD-ROM's PAD field is something else).
        fileFrame += static_cast<uint32_t>(frames) + static_cast<uint32_t>((CD_TRACK_PADDING - frames % CD_TRACK_PADDING) % CD_TRACK_PADDING);
        tracks.push_back(t);
    }
    if (tracks.empty() && get(DVD_METADATA_TAG, 0)) {
        // A DVD (PS2): one data track of 2048-byte sectors.
        Track t{};
        t.number = 1;
        t.frames = static_cast<uint32_t>(chd_get_header(chd)->logicalbytes / 2048);
        tracks.push_back(t);
    }
    return tracks;
}

ChdTrack *openChd(const char *path, uint32_t number) {
    auto track = std::make_unique<ChdTrack>();
    track->file = openPath(path);
    if (!track->file || chd_open_file(track->file, CHD_OPEN_READ, nullptr, &track->chd) != CHDERR_NONE) return nullptr;
    const chd_header *header = chd_get_header(track->chd);
    track->hunkBytes = header->hunkbytes;
    std::vector<Track> tracks = readTracks(track->chd);
    // Older (v3/v4) CHDs don't record the unit size: a CD frame with its subcode, or a DVD sector.
    bool dvd = tracks.size() == 1 && tracks[0].dataOffset == 0 && !tracks[0].audio && header->unitbytes == 2048;
    track->unitBytes = header->unitbytes ? header->unitbytes : (dvd ? 2048 : CD_FRAME_SIZE);
    if (track->hunkBytes == 0 || track->hunkBytes % track->unitBytes) return nullptr;
    track->hunk.resize(track->hunkBytes);
    const Track *found = nullptr;
    switch (number) {
        case RC_HASH_CDTRACK_FIRST_DATA:
            for (const Track &t : tracks) if (!t.audio) { found = &t; break; }
            break;
        case RC_HASH_CDTRACK_LAST:
            if (!tracks.empty()) found = &tracks.back();
            break;
        case RC_HASH_CDTRACK_LARGEST:
            for (const Track &t : tracks) if (!found || t.frames > found->frames) found = &t;
            break;
        case RC_HASH_CDTRACK_FIRST_OF_SECOND_SESSION:
            // Sessions aren't recorded in a CHD; on a GD-ROM the second one starts at track 3.
            for (const Track &t : tracks) if (t.number == 3 && t.lba == 45000) { found = &t; break; }
            break;
        default:
            for (const Track &t : tracks) if (t.number == static_cast<int>(number)) { found = &t; break; }
    }
    if (!found) return nullptr;
    track->track = *found;
    return track.release();
}

void *RC_CCONV openTrackIterator(const char *path, uint32_t number, const rc_hash_iterator *iterator) {
    auto handle = std::make_unique<Handle>();
    if (isChd(path)) {
        handle->chd.reset(openChd(path, number));
        if (!handle->chd) return nullptr;
    } else {
        handle->other = defaultReader.open_track_iterator ? defaultReader.open_track_iterator(path, number, iterator)
                                                          : defaultReader.open_track ? defaultReader.open_track(path, number) : nullptr;
        if (!handle->other) return nullptr;
    }
    return handle.release();
}

void *RC_CCONV openTrack(const char *path, uint32_t number) { return openTrackIterator(path, number, nullptr); }

size_t RC_CCONV readSector(void *h, uint32_t sector, void *buffer, size_t requested) {
    auto *handle = static_cast<Handle *>(h);
    if (!handle) return 0;
    if (!handle->chd) return defaultReader.read_sector(handle->other, sector, buffer, requested);
    ChdTrack &c = *handle->chd;
    const Track &t = c.track;
    if (sector < t.lba) return 0;
    uint32_t frame = t.chdFrame + (sector - t.lba);
    auto *out = static_cast<uint8_t *>(buffer);
    size_t total = 0;
    while (requested > 0) {
        if (frame >= t.chdFrame + t.frames) break;
        const uint8_t *data = c.frame(frame);
        if (!data) break;
        size_t n = std::min<size_t>(requested, static_cast<size_t>(t.dataSize));
        if (static_cast<size_t>(t.dataOffset) + n > c.unitBytes) n = c.unitBytes - t.dataOffset;
        memcpy(out + total, data + t.dataOffset, n);
        total += n;
        requested -= n;
        frame++;
    }
    return total;
}

void RC_CCONV closeTrack(void *h) {
    auto *handle = static_cast<Handle *>(h);
    if (!handle) return;
    if (handle->other && defaultReader.close_track) defaultReader.close_track(handle->other);
    delete handle;
}

uint32_t RC_CCONV firstTrackSector(void *h) {
    auto *handle = static_cast<Handle *>(h);
    if (!handle) return 0;
    if (handle->chd) return handle->chd->track.lba;
    return defaultReader.first_track_sector ? defaultReader.first_track_sector(handle->other) : 0;
}

// rcheevos' file reading, through [openFile].
void *RC_CCONV fileOpen(const char *path) { return openPath(path); }
void RC_CCONV fileSeek(void *f, int64_t offset, int origin) { fseeko(static_cast<FILE *>(f), offset, origin); }
int64_t RC_CCONV fileTell(void *f) { return ftello(static_cast<FILE *>(f)); }
size_t RC_CCONV fileRead(void *f, void *buffer, size_t bytes) { return fread(buffer, 1, bytes, static_cast<FILE *>(f)); }
void RC_CCONV fileClose(void *f) { fclose(static_cast<FILE *>(f)); }

} // namespace

namespace cheevos {

void installChdReader(FILE *(*open)(const char *path)) {
    static bool installed = false;
    if (installed) return;
    installed = true;
    openFile = open;
    rc_hash_filereader files{};
    files.open = fileOpen;
    files.seek = fileSeek;
    files.tell = fileTell;
    files.read = fileRead;
    files.close = fileClose;
    rc_hash_init_custom_filereader(&files);
    rc_hash_get_default_cdreader(&defaultReader);
    rc_hash_cdreader_t reader{};
    reader.open_track = openTrack;
    reader.open_track_iterator = openTrackIterator;
    reader.read_sector = readSector;
    reader.close_track = closeTrack;
    reader.first_track_sector = firstTrackSector;
    rc_hash_init_custom_cdreader(&reader);
}

} // namespace cheevos

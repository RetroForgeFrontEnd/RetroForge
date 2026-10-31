// The player's libretro VFS: cores that open files through it read "streamed" games straight from a file RetroForge
// already has open (a game on a network drive or in an Android folder), so nothing has to be copied first. Every
// other path is an ordinary file.

#include "vfs.h"

#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <cstdio>
#include <map>
#include <mutex>
#include <string>
#include <vector>

struct retro_vfs_file_handle {
    int fd = -1;
    int64_t pos = 0;
    std::string path;
    bool streamed = false;
};

struct retro_vfs_dir_handle {
    DIR *dir = nullptr;
    std::string path;
    // Streamed files in this folder, listed after the real ones.
    std::vector<std::string> extra;
    size_t extraAt = 0;
    std::string name;
    bool isDir = false;
};

namespace {

std::mutex streamsMutex;
std::map<std::string, int> streams;  // path -> fd (ours; dup'ed per open)
std::atomic<bool> requested{false};

int streamFd(const std::string &path) {
    std::lock_guard<std::mutex> lock(streamsMutex);
    auto it = streams.find(path);
    return it == streams.end() ? -1 : it->second;
}

std::string parentOf(const std::string &path) {
    auto slash = path.find_last_of('/');
    return slash == std::string::npos ? std::string() : path.substr(0, slash);
}

const char *getPath(retro_vfs_file_handle *h) { return h ? h->path.c_str() : nullptr; }

retro_vfs_file_handle *openFile(const char *path, unsigned mode, unsigned) {
    if (!path) return nullptr;
    int stream = streamFd(path);
    if (stream >= 0) {
        if (mode & RETRO_VFS_FILE_ACCESS_WRITE) return nullptr;  // streamed games are read-only
        int fd = dup(stream);
        if (fd < 0) return nullptr;
        return new retro_vfs_file_handle{fd, 0, path, true};
    }
    int flags;
    if ((mode & RETRO_VFS_FILE_ACCESS_READ_WRITE) == RETRO_VFS_FILE_ACCESS_READ_WRITE) flags = O_RDWR | O_CREAT;
    else if (mode & RETRO_VFS_FILE_ACCESS_WRITE) flags = O_WRONLY | O_CREAT;
    else flags = O_RDONLY;
    if ((mode & RETRO_VFS_FILE_ACCESS_WRITE) && !(mode & RETRO_VFS_FILE_ACCESS_UPDATE_EXISTING)) flags |= O_TRUNC;
    int fd = open(path, flags | O_CLOEXEC, 0644);
    if (fd < 0) return nullptr;
    return new retro_vfs_file_handle{fd, 0, path, false};
}

int closeFile(retro_vfs_file_handle *h) {
    if (!h) return -1;
    int result = close(h->fd);
    delete h;
    return result == 0 ? 0 : -1;
}

int64_t sizeOf(retro_vfs_file_handle *h) {
    struct stat st{};
    if (!h || fstat(h->fd, &st) != 0) return -1;
    return st.st_size;
}

int64_t tell(retro_vfs_file_handle *h) { return h ? h->pos : -1; }

int64_t seek(retro_vfs_file_handle *h, int64_t offset, int whence) {
    if (!h) return -1;
    int64_t base = whence == RETRO_VFS_SEEK_POSITION_START ? 0 : whence == RETRO_VFS_SEEK_POSITION_CURRENT ? h->pos : sizeOf(h);
    if (base < 0 || base + offset < 0) return -1;
    h->pos = base + offset;
    return h->pos;
}

int64_t readFile(retro_vfs_file_handle *h, void *buffer, uint64_t length) {
    if (!h) return -1;
    auto *out = static_cast<uint8_t *>(buffer);
    uint64_t done = 0;
    while (done < length) {
        ssize_t n = pread(h->fd, out + done, length - done, h->pos + int64_t(done));
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) return done > 0 ? int64_t(done) : -1;
        if (n == 0) break;
        done += uint64_t(n);
    }
    h->pos += int64_t(done);
    return int64_t(done);
}

int64_t writeFile(retro_vfs_file_handle *h, const void *buffer, uint64_t length) {
    if (!h || h->streamed) return -1;
    ssize_t n = pwrite(h->fd, buffer, length, h->pos);
    if (n < 0) return -1;
    h->pos += n;
    return n;
}

int flush(retro_vfs_file_handle *h) { return h ? 0 : -1; }

int removePath(const char *path) {
    if (!path) return -1;
    return (unlink(path) == 0 || rmdir(path) == 0) ? 0 : -1;
}

int renamePath(const char *from, const char *to) { return from && to && rename(from, to) == 0 ? 0 : -1; }

int64_t truncateFile(retro_vfs_file_handle *h, int64_t length) {
    return h && !h->streamed && ftruncate(h->fd, length) == 0 ? 0 : -1;
}

int stat64Path(const char *path, int64_t *size) {
    if (!path) return 0;
    int stream = streamFd(path);
    if (stream >= 0) {
        struct stat st{};
        if (fstat(stream, &st) != 0) return 0;
        if (size) *size = st.st_size;
        return RETRO_VFS_STAT_IS_VALID | RETRO_VFS_STAT_IS_READONLY;
    }
    struct stat st{};
    if (stat(path, &st) != 0) return 0;
    if (size) *size = st.st_size;
    int flags = RETRO_VFS_STAT_IS_VALID;
    if (S_ISDIR(st.st_mode)) flags |= RETRO_VFS_STAT_IS_DIRECTORY;
    if (S_ISCHR(st.st_mode)) flags |= RETRO_VFS_STAT_IS_CHARACTER_SPECIAL;
    return flags;
}

int statPath(const char *path, int32_t *size) {
    int64_t big = 0;
    int flags = stat64Path(path, &big);
    if (size) *size = int32_t(big);
    return flags;
}

int makeDir(const char *dir) {
    if (!dir) return -1;
    if (mkdir(dir, 0755) == 0) return 0;
    return errno == EEXIST ? -2 : -1;
}

retro_vfs_dir_handle *openDir(const char *dir, bool) {
    if (!dir) return nullptr;
    auto *h = new retro_vfs_dir_handle{};
    h->path = dir;
    while (h->path.size() > 1 && h->path.back() == '/') h->path.pop_back();
    h->dir = opendir(dir);
    {
        std::lock_guard<std::mutex> lock(streamsMutex);
        for (auto &[path, fd] : streams)
            if (parentOf(path) == h->path) h->extra.push_back(path.substr(h->path.size() + 1));
    }
    if (!h->dir && h->extra.empty()) {
        delete h;
        return nullptr;
    }
    return h;
}

bool readDir(retro_vfs_dir_handle *h) {
    if (!h) return false;
    while (h->dir) {
        dirent *entry = readdir(h->dir);
        if (!entry) break;
        std::string name = entry->d_name;
        if (name == "." || name == "..") continue;
        h->name = name;
        h->isDir = entry->d_type == DT_DIR;
        return true;
    }
    if (h->extraAt < h->extra.size()) {
        h->name = h->extra[h->extraAt++];
        h->isDir = false;
        return true;
    }
    return false;
}

const char *direntName(retro_vfs_dir_handle *h) { return h ? h->name.c_str() : nullptr; }

bool direntIsDir(retro_vfs_dir_handle *h) { return h && h->isDir; }

int closeDir(retro_vfs_dir_handle *h) {
    if (!h) return -1;
    if (h->dir) closedir(h->dir);
    delete h;
    return 0;
}

retro_vfs_interface interface = {
    getPath, openFile, closeFile, sizeOf, tell, seek, readFile, writeFile, flush, removePath, renamePath,
    truncateFile,
    statPath, makeDir, openDir, readDir, direntName, direntIsDir, closeDir,
    stat64Path,
    nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr,
};

}  // namespace

namespace vfs {

bool provide(retro_vfs_interface_info *info) {
    if (!info || info->required_interface_version > 4) return false;
    info->iface = &interface;
    requested = true;
    return true;
}

bool wasRequested() { return requested; }

void resetRequested() { requested = false; }

void addStream(const std::string &path, int fd) {
    int own = dup(fd);
    if (own < 0) return;
    std::lock_guard<std::mutex> lock(streamsMutex);
    auto it = streams.find(path);
    if (it != streams.end()) close(it->second);
    streams[path] = own;
}

int duplicateStream(const std::string &path) {
    int fd = streamFd(path);
    return fd < 0 ? -1 : dup(fd);
}

void clearStreams() {
    std::lock_guard<std::mutex> lock(streamsMutex);
    for (auto &[path, fd] : streams) close(fd);
    streams.clear();
}

}  // namespace vfs

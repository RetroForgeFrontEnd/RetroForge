package com.retroforge.library.manage

/**
 * Reads Dreamcast .gdi sheets the way chdman 0.287 does (MAME's cdrom_file::parse_gdi): one line per track giving
 * its start LBA, type (4 data, 0 audio), sector size and file. The gap before a track is stored as zero frames at the
 * end of the track before it, so every track starts at its LBA (the gap before the high-density area at LBA 45000
 * included). Newer MAME turns the smaller gaps into pregaps instead; Flycast reads both, and 0.287's layout is the
 * one most Dreamcast CHDs use.
 */
object GdiSheet {
    fun files(text: String): List<String> = lines(text).drop(1).map { it[4] }.distinct()

    fun tracks(text: String, fileSize: (String) -> Long?): List<CdTrack> {
        val rows = lines(text)
        val count = rows.firstOrNull()?.firstOrNull()?.toIntOrNull() ?: throw UnsupportedDiscException("GDI doesn't have a track count")
        val entries = rows.drop(1).sortedBy { it[0].toIntOrNull() ?: 0 }
        if (entries.size != count || entries.map { it[0].toIntOrNull() } != (1..count).toList()) throw UnsupportedDiscException("GDI is missing tracks")
        val tracks = mutableListOf<CdTrack>()
        var previousEnd = 0L
        for ((i, row) in entries.withIndex()) {
            if (row.size != 6) throw UnsupportedDiscException("GDI track ${i + 1} should have 6 fields")
            val lba = row[1].toLongOrNull() ?: throw UnsupportedDiscException("bad LBA for track ${i + 1}")
            val sector = row[3].toIntOrNull()
            val type = when {
                row[2] == "4" && sector == 2352 -> TrackType.MODE1_RAW
                row[2] == "4" && sector == 2048 -> TrackType.MODE1
                row[2] == "0" -> TrackType.AUDIO
                else -> throw UnsupportedDiscException("GDI track ${i + 1} has an unknown type")
            }
            val file = row[4]
            val size = fileSize(file) ?: throw UnsupportedDiscException("missing file $file")
            val frames = (size / type.sectorSize).toInt()
            if (frames <= 0) throw UnsupportedDiscException("track ${i + 1} is empty")
            if (i > 0) {
                val gap = lba - previousEnd
                if (gap < 0) throw UnsupportedDiscException("GDI track ${i + 1} overlaps the track before it")
                val before = tracks.removeAt(tracks.lastIndex)
                tracks += before.copy(frames = before.frames + gap.toInt(), padFrames = gap.toInt())
            }
            tracks += CdTrack(type, file, 0, frames, pregap = 0, pregapInData = false, postgap = 0)
            previousEnd = lba + frames
        }
        return tracks
    }

    /** Tokens per non-empty line; a quoted file name stays one token. */
    private fun lines(text: String): List<List<String>> = text.lineSequence().map { line ->
        Regex("\"([^\"]*)\"|(\\S+)").findAll(line).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
    }.filter { it.isNotEmpty() }.toList()
}

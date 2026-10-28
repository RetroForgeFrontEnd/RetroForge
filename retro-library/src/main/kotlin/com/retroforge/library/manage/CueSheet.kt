package com.retroforge.library.manage

import java.io.IOException
import java.io.InputStream

enum class TrackType(val cueName: String, val chdName: String, val sectorSize: Int) {
    MODE1("MODE1/2048", "MODE1", 2048),
    MODE1_RAW("MODE1/2352", "MODE1_RAW", 2352),
    MODE2("MODE2/2336", "MODE2", 2336),
    MODE2_FORM1("MODE2/2048", "MODE2_FORM1", 2048),
    MODE2_FORM2("MODE2/2324", "MODE2_FORM2", 2324),
    MODE2_RAW("MODE2/2352", "MODE2_RAW", 2352),
    AUDIO("AUDIO", "AUDIO", 2352),
}

/** One track as CHD stores it: [frames] frames read from [file] at [startByte] (in-file pregap included). */
data class CdTrack(
    val type: TrackType,
    val file: String,
    val startByte: Long,
    val frames: Int,
    val pregap: Int,
    val pregapInData: Boolean,
    val postgap: Int,
) {
    val paddedFrames: Int get() = (frames + Chd.CD_TRACK_PADDING - 1) / Chd.CD_TRACK_PADDING * Chd.CD_TRACK_PADDING
}

class UnsupportedDiscException(message: String) : IOException(message)

/** Reads .cue sheets (BINARY files only; WAVE/MP3 audio tracks are refused). */
object CueSheet {

    private class Entry(val file: String, val number: Int, val type: TrackType) {
        var index0: Int? = null
        var index1: Int? = null
        var pregap = 0
        var postgap = 0
    }

    /** Files the sheet refers to, as written (relative to the sheet's folder). */
    fun files(text: String): List<String> = text.lineSequence().mapNotNull { fileName(it.trim()) }.distinct().toList()

    /**
     * Tracks in disc order. [fileSize] returns the size of a referenced file (relative to the sheet's folder),
     * or null when it's missing.
     */
    fun tracks(text: String, fileSize: (String) -> Long?): List<CdTrack> {
        val entries = mutableListOf<Entry>()
        var file: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val words = line.split(Regex("\\s+"))
            when (words.firstOrNull()?.uppercase()) {
                "FILE" -> {
                    file = fileName(line) ?: throw UnsupportedDiscException("unreadable FILE line: $line")
                    val kind = line.substringAfterLast('"', line).trim().split(Regex("\\s+")).last().uppercase()
                    if (kind != "BINARY" && kind != "MOTOROLA") throw UnsupportedDiscException("$kind audio files aren't supported")
                    if (kind == "MOTOROLA") throw UnsupportedDiscException("big-endian (MOTOROLA) files aren't supported")
                }
                "TRACK" -> {
                    val f = file ?: throw UnsupportedDiscException("TRACK before FILE")
                    val typeName = words.getOrNull(2)?.uppercase() ?: throw UnsupportedDiscException("TRACK without a type")
                    val type = TrackType.entries.firstOrNull { it.cueName == typeName } ?: throw UnsupportedDiscException("$typeName tracks aren't supported")
                    entries += Entry(f, words.getOrNull(1)?.toIntOrNull() ?: 0, type)
                }
                "INDEX" -> {
                    val e = entries.lastOrNull() ?: continue
                    val at = msf(words.getOrNull(2)) ?: throw UnsupportedDiscException("bad INDEX: $line")
                    when (words.getOrNull(1)?.toIntOrNull()) {
                        0 -> e.index0 = at
                        1 -> e.index1 = at
                    }
                }
                "PREGAP" -> entries.lastOrNull()?.pregap = msf(words.getOrNull(1)) ?: 0
                "POSTGAP" -> entries.lastOrNull()?.postgap = msf(words.getOrNull(1)) ?: 0
            }
        }
        if (entries.isEmpty()) throw UnsupportedDiscException("no tracks")
        return entries.mapIndexed { i, e ->
            val index1 = e.index1 ?: throw UnsupportedDiscException("track ${e.number} has no INDEX 01")
            val start = e.index0 ?: index1
            val size = fileSize(e.file) ?: throw UnsupportedDiscException("missing file ${e.file}")
            val next = entries.getOrNull(i + 1)?.takeIf { it.file == e.file }
            val endFrame = if (next != null) next.index0 ?: next.index1 ?: throw UnsupportedDiscException("track ${next.number} has no INDEX 01")
            else {
                if (size % e.type.sectorSize != 0L) throw UnsupportedDiscException("${e.file} isn't a whole number of sectors")
                (size / e.type.sectorSize).toInt()
            }
            if (endFrame <= start) throw UnsupportedDiscException("track ${e.number} is empty")
            if (e.index0 != null && e.pregap > 0) throw UnsupportedDiscException("track ${e.number} has both PREGAP and INDEX 00")
            CdTrack(
                type = e.type,
                file = e.file,
                startByte = start.toLong() * e.type.sectorSize,
                frames = endFrame - start,
                pregap = if (e.index0 != null) index1 - start else e.pregap,
                pregapInData = e.index0 != null,
                postgap = e.postgap,
            )
        }
    }

    private fun fileName(line: String): String? {
        if (!line.startsWith("FILE", ignoreCase = true)) return null
        val quoted = Regex("^FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
        return (quoted ?: Regex("^FILE\\s+(\\S+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1))
            ?.replace('\\', '/')?.removePrefix("./")
    }

    private fun msf(s: String?): Int? {
        val p = s?.split(':')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 } ?: return null
        return (p[0] * 60 + p[1]) * 75 + p[2]
    }

    fun logicalBytes(tracks: List<CdTrack>): Long = tracks.sumOf { it.paddedFrames.toLong() } * Chd.CD_FRAME

    /**
     * The disc as CHD lays it out: each frame is the sector (zero-padded to 2352 bytes) plus 96 bytes of empty
     * subcode, audio samples byte-swapped to big-endian, and every track padded to a multiple of 4 frames.
     */
    fun cdStream(tracks: List<CdTrack>, open: (String) -> InputStream): InputStream = object : InputStream() {
        private var track = -1
        private var frameInTrack = 0
        private var source: InputStream? = null
        private val frame = ByteArray(Chd.CD_FRAME)
        private var framePos = Chd.CD_FRAME

        private fun nextFrame(): Boolean {
            while (track < 0 || frameInTrack >= tracks[track].paddedFrames) {
                source?.close()
                source = null
                track++
                frameInTrack = 0
                if (track >= tracks.size) return false
                val t = tracks[track]
                source = open(t.file).also { skipFully(it, t.startByte) }
            }
            val t = tracks[track]
            frame.fill(0)
            if (frameInTrack < t.frames) {
                Chd.readFully(source!!, frame, t.type.sectorSize)
                if (t.type == TrackType.AUDIO) {
                    var i = 0
                    while (i < Chd.CD_SECTOR) { val b = frame[i]; frame[i] = frame[i + 1]; frame[i + 1] = b; i += 2 }
                }
            }
            frameInTrack++
            framePos = 0
            return true
        }

        override fun read(): Int {
            if (framePos >= Chd.CD_FRAME && !nextFrame()) return -1
            return frame[framePos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (framePos >= Chd.CD_FRAME && !nextFrame()) return -1
            val n = minOf(len, Chd.CD_FRAME - framePos)
            System.arraycopy(frame, framePos, b, off, n)
            framePos += n
            return n
        }

        override fun close() { source?.close() }
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var left = bytes
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) {
                if (input.read() < 0) throw IOException("file ended before its track")
                left--
            } else left -= s
        }
    }
}

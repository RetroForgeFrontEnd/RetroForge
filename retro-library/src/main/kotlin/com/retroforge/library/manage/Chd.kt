package com.retroforge.library.manage

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * CHD v5 (MAME's "Compressed Hunks of Data"), the disc format RetroArch, DuckStation, Flycast, PCSX2/NetherSX2 and
 * PPSSPP read. RetroForge writes it with Deflate only ("cdzl" for CDs, "zlib" for DVDs), which every CHD reader
 * supports; files made by chdman with LZMA/FLAC are a few percent smaller. As chdman does, sectors whose sync and
 * parity can be rebuilt are stored without them, and a hunk identical to an earlier one is stored as a reference. Layout and checksums follow chdman, so
 * `chdman verify` accepts the result and the data SHA-1 equals chdman's for the same disc.
 */
object Chd {
    const val CD_FRAME = 2448
    const val CD_SECTOR = 2352
    const val CD_SUBCODE = 96
    const val CD_HUNK = 8 * CD_FRAME
    const val CD_TRACK_PADDING = 4
    const val DVD_SECTOR = 2048
    const val DVD_HUNK = 2 * DVD_SECTOR

    internal const val HEADER_BYTES = 124
    internal val CODEC_CDZL = fourcc("cdzl")
    internal val CODEC_ZLIB = fourcc("zlib")
    internal const val TYPE_CODEC0 = 0
    internal const val TYPE_NONE = 4
    internal const val TYPE_SELF = 5
    private const val METADATA_CHECKSUM = 0x01

    fun fourcc(s: String): Int = s.fold(0) { acc, c -> (acc shl 8) or c.code }

    class Metadata(val tag: Int, val data: ByteArray)

    fun cdTrackMetadata(index: Int, track: CdTrack): Metadata {
        val pgType = if (track.pregap > 0 && track.pregapInData) "V${track.type.chdName}" else "MODE1"
        val text = "TRACK:${index + 1} TYPE:${track.type.chdName} SUBTYPE:NONE FRAMES:${track.frames} PREGAP:${track.pregap} " +
            "PGTYPE:$pgType PGSUB:NONE POSTGAP:${track.postgap}"
        return Metadata(fourcc("CHT2"), (text + "\u0000").toByteArray(Charsets.US_ASCII))
    }

    /** GD-ROM track metadata ("CHGD"), as chdman writes it for .gdi sheets. */
    fun gdTrackMetadata(index: Int, track: CdTrack): Metadata {
        val text = "TRACK:${index + 1} TYPE:${track.type.chdName} SUBTYPE:NONE FRAMES:${track.frames} PAD:${track.padFrames} " +
            "PREGAP:${track.pregap} PGTYPE:MODE1 PGSUB:NONE POSTGAP:${track.postgap}"
        return Metadata(fourcc("CHGD"), (text + "\u0000").toByteArray(Charsets.US_ASCII))
    }

    val dvdMetadata: Metadata get() = Metadata(fourcc("DVD "), byteArrayOf(0))

    /** Result of a write: the checksum of the uncompressed data, for verification. */
    class Written(val dataSha1: ByteArray, val bytes: Long)

    /**
     * Writes a CHD to [out] (positioned at 0, empty). [data] supplies exactly [logicalBytes] bytes. Hunks are
     * compressed on all cores; [onProgress] gets bytes consumed.
     */
    fun write(
        out: SeekableByteChannel,
        data: InputStream,
        logicalBytes: Long,
        hunkBytes: Int,
        unitBytes: Int,
        cd: Boolean,
        metadata: List<Metadata>,
        onProgress: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Written {
        val hunkCount = ((logicalBytes + hunkBytes - 1) / hunkBytes).toInt()
        val rawSha1 = MessageDigest.getInstance("SHA-1")
        val types = ByteArray(hunkCount)
        val lengths = IntArray(hunkCount)
        val crcs = IntArray(hunkCount)
        val refs = IntArray(hunkCount)
        val seen = HashMap<ByteBuffer, Int>()
        out.position(0)
        writeFully(out, ByteBuffer.allocate(HEADER_BYTES))
        var offset = HEADER_BYTES.toLong()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            var hunk = 0
            var consumed = 0L
            val batch = threads * 16
            while (hunk < hunkCount) {
                if (isCancelled()) throw CancelledException()
                val inputs = ArrayList<ByteArray>(batch)
                while (inputs.size < batch && hunk + inputs.size < hunkCount) {
                    val buf = ByteArray(hunkBytes)
                    val want = minOf(hunkBytes.toLong(), logicalBytes - consumed).toInt()
                    readFully(data, buf, want)
                    rawSha1.update(buf, 0, want)
                    consumed += want
                    inputs += buf
                }
                // Hunks already stored become references; only new ones get compressed.
                val firsts = IntArray(inputs.size) { -1 }
                for ((i, buf) in inputs.withIndex()) {
                    val key = ByteBuffer.wrap(MessageDigest.getInstance("SHA-1").digest(buf))
                    val earlier = seen[key]
                    if (earlier != null) firsts[i] = earlier else seen[key] = hunk + i
                }
                val fresh = inputs.indices.filter { firsts[it] < 0 }
                val packedFresh = pool.invokeAll(fresh.map { i -> Callable { val buf = inputs[i]; if (cd) compressCd(buf) else deflateOrNull(buf, 0, buf.size, hunkBytes) } }).map { it.get() }
                val compressed = arrayOfNulls<ByteArray>(inputs.size)
                fresh.forEachIndexed { k, i -> compressed[i] = packedFresh[k] }
                for (i in inputs.indices) {
                    val n = hunk + i
                    crcs[n] = crc16(inputs[i], 0, hunkBytes)
                    val packed = compressed[i]
                    when {
                        firsts[i] >= 0 -> {
                            types[n] = TYPE_SELF.toByte()
                            refs[n] = firsts[i]
                        }
                        packed != null -> {
                            types[n] = TYPE_CODEC0.toByte()
                            lengths[n] = packed.size
                            writeFully(out, ByteBuffer.wrap(packed))
                            offset += packed.size
                        }
                        else -> {
                            types[n] = TYPE_NONE.toByte()
                            lengths[n] = hunkBytes
                            writeFully(out, ByteBuffer.wrap(inputs[i]))
                            offset += hunkBytes
                        }
                    }
                }
                hunk += inputs.size
                onProgress(consumed)
            }
        } finally {
            pool.shutdownNow()
        }

        val mapOffset = offset
        val map = encodeMap(types, lengths, crcs, refs, hunkBytes, HEADER_BYTES.toLong())
        writeFully(out, ByteBuffer.wrap(map))
        offset += map.size

        val metaOffset = if (metadata.isEmpty()) 0L else offset
        for ((i, m) in metadata.withIndex()) {
            val next = if (i == metadata.lastIndex) 0L else offset + 16 + m.data.size
            val entry = ByteBuffer.allocate(16 + m.data.size)
            entry.putInt(m.tag)
            entry.putInt((METADATA_CHECKSUM shl 24) or m.data.size)
            entry.putLong(next)
            entry.put(m.data)
            entry.flip()
            writeFully(out, entry)
            offset += 16 + m.data.size
        }

        val dataSha1 = rawSha1.digest()
        val header = ByteBuffer.allocate(HEADER_BYTES)
        header.put("MComprHD".toByteArray(Charsets.US_ASCII))
        header.putInt(HEADER_BYTES)
        header.putInt(5)
        header.putInt(if (cd) CODEC_CDZL else CODEC_ZLIB).putInt(0).putInt(0).putInt(0)
        header.putLong(logicalBytes)
        header.putLong(mapOffset)
        header.putLong(metaOffset)
        header.putInt(hunkBytes)
        header.putInt(unitBytes)
        header.put(dataSha1)
        header.put(overallSha1(dataSha1, metadata))
        header.put(ByteArray(20))
        header.flip()
        out.position(0)
        writeFully(out, header)
        return Written(dataSha1, offset)
    }

    internal fun overallSha1(dataSha1: ByteArray, metadata: List<Metadata>): ByteArray {
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(dataSha1)
        metadata.map { m ->
            ByteBuffer.allocate(24).putInt(m.tag).put(MessageDigest.getInstance("SHA-1").digest(m.data)).array()
        }.sortedWith { a, b -> compareUnsigned(a, b) }.forEach(sha::update)
        return sha.digest()
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in a.indices) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return 0
    }

    /**
     * "cdzl": sector data and subcode deflated separately, after a header of flags (one bit per frame: sync and
     * parity removed, to be rebuilt) and the data length.
     */
    private fun compressCd(hunk: ByteArray): ByteArray? {
        val frames = hunk.size / CD_FRAME
        val sectors = ByteArray(frames * CD_SECTOR)
        val subcode = ByteArray(frames * CD_SUBCODE)
        val eccBytes = (frames + 7) / 8
        val flags = ByteArray(eccBytes)
        for (f in 0 until frames) {
            System.arraycopy(hunk, f * CD_FRAME, sectors, f * CD_SECTOR, CD_SECTOR)
            System.arraycopy(hunk, f * CD_FRAME + CD_SECTOR, subcode, f * CD_SUBCODE, CD_SUBCODE)
            if (CdEcc.verify(sectors, f * CD_SECTOR)) {
                flags[f / 8] = (flags[f / 8].toInt() or (1 shl (f % 8))).toByte()
                CdEcc.clear(sectors, f * CD_SECTOR)
            }
        }
        val base = deflateOrNull(sectors, 0, sectors.size, hunk.size) ?: return null
        val sub = deflateOrNull(subcode, 0, subcode.size, hunk.size) ?: return null
        val total = eccBytes + 2 + base.size + sub.size
        if (total >= hunk.size) return null
        val out = ByteArray(total)
        flags.copyInto(out)
        out[eccBytes] = (base.size shr 8).toByte()
        out[eccBytes + 1] = base.size.toByte()
        System.arraycopy(base, 0, out, eccBytes + 2, base.size)
        System.arraycopy(sub, 0, out, eccBytes + 2 + base.size, sub.size)
        return out
    }

    /** Raw deflate (no zlib header), as chdman writes it; null when it wouldn't be smaller than [limit]. */
    private fun deflateOrNull(src: ByteArray, from: Int, len: Int, limit: Int): ByteArray? {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(src, from, len)
            deflater.finish()
            val buf = ByteArray(limit)
            var n = 0
            while (!deflater.finished()) {
                if (n >= limit) return null
                n += deflater.deflate(buf, n, limit - n)
            }
            return if (n >= limit) null else buf.copyOf(n)
        } finally {
            deflater.end()
        }
    }

    /**
     * The v5 compressed map: a Huffman tree (all 16 codes 4 bits long, so each code equals its symbol), each hunk's
     * type, then each hunk's length and CRC-16, or for a reference the hunk it repeats. Parent references and the
     * shorter self-reference forms aren't used.
     */
    internal fun encodeMap(types: ByteArray, lengths: IntArray, crcs: IntArray, refs: IntArray, hunkBytes: Int, firstOffset: Long): ByteArray {
        val maxLength = types.indices.filter { types[it].toInt() == TYPE_CODEC0 }.maxOfOrNull { lengths[it] } ?: 0
        val lengthBits = if (maxLength == 0) 0 else 32 - Integer.numberOfLeadingZeros(maxLength)
        val maxRef = types.indices.filter { types[it].toInt() == TYPE_SELF }.maxOfOrNull { refs[it] } ?: 0
        val selfBits = if (maxRef == 0) 0 else 32 - Integer.numberOfLeadingZeros(maxRef)
        val bits = BitWriter()
        repeat(16) { bits.write(4, 4) }
        for (t in types) bits.write(t.toInt(), 4)
        val raw = ByteArray(types.size * 12)
        var offset = firstOffset
        for (i in types.indices) {
            val type = types[i].toInt()
            val e = i * 12
            raw[e] = type.toByte()
            if (type == TYPE_SELF) {
                bits.write(refs[i], selfBits)
                putBig(raw, e + 4, refs[i].toLong(), 6)
                continue
            }
            if (type == TYPE_CODEC0) bits.write(lengths[i], lengthBits)
            bits.write(crcs[i], 16)
            val length = if (type == TYPE_NONE) hunkBytes else lengths[i]
            putBig(raw, e + 1, length.toLong(), 3)
            putBig(raw, e + 4, offset, 6)
            putBig(raw, e + 10, crcs[i].toLong(), 2)
            offset += length
        }
        val body = bits.toByteArray()
        val header = ByteArray(16)
        putBig(header, 0, body.size.toLong(), 4)
        putBig(header, 4, firstOffset, 6)
        putBig(header, 10, crc16(raw, 0, raw.size).toLong(), 2)
        header[12] = lengthBits.toByte()
        header[13] = selfBits.toByte()
        header[14] = 0
        return header + body
    }

    /** CRC-16/CCITT-FALSE, as chdman uses for hunks and the map. */
    fun crc16(data: ByteArray, from: Int, len: Int): Int {
        var crc = 0xFFFF
        for (i in from until from + len) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }

    private fun putBig(dst: ByteArray, at: Int, value: Long, bytes: Int) {
        for (i in 0 until bytes) dst[at + i] = (value shr (8 * (bytes - 1 - i))).toByte()
    }

    internal fun readFully(input: InputStream, buf: ByteArray, len: Int) {
        var n = 0
        while (n < len) {
            val r = input.read(buf, n, len - n)
            if (r < 0) throw EOFException("the disc image ended early")
            n += r
        }
    }

    private fun writeFully(out: SeekableByteChannel, buf: ByteBuffer) {
        while (buf.hasRemaining()) out.write(buf)
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var acc = 0L
        private var count = 0

        fun write(value: Int, bits: Int) {
            if (bits == 0) return
            acc = (acc shl bits) or (value.toLong() and ((1L shl bits) - 1))
            count += bits
            while (count >= 8) {
                out.write((acc shr (count - 8)).toInt() and 0xFF)
                count -= 8
            }
        }

        fun toByteArray(): ByteArray {
            if (count > 0) { out.write((acc shl (8 - count)).toInt() and 0xFF); count = 0 }
            return out.toByteArray()
        }
    }
}

class CancelledException : Exception("cancelled")

/**
 * Reads back the CHDs RetroForge writes (Deflate codecs, uncompressed hunks), to check every hunk and the data
 * SHA-1 before the original files are moved aside. Not a general CHD reader: LZMA/FLAC files are refused.
 */
object ChdVerifier {

    class Report(val dataSha1: ByteArray, val logicalBytes: Long)

    fun verify(channel: SeekableByteChannel, onProgress: (Long) -> Unit = {}, isCancelled: () -> Boolean = { false }): Report {
        val header = read(channel, 0, Chd.HEADER_BYTES)
        if (String(header, 0, 8, Charsets.US_ASCII) != "MComprHD" || header.int(12) != 5) throw IOException("not a CHD v5 file")
        val codec = header.int(16)
        val logical = header.long(32)
        val mapOffset = header.long(40)
        val hunkBytes = header.int(56)
        val expectedSha1 = header.copyOfRange(64, 84)
        val hunkCount = ((logical + hunkBytes - 1) / hunkBytes).toInt()

        val mapHeader = read(channel, mapOffset, 16)
        val mapBytes = mapHeader.int(0)
        val lengthBits = mapHeader[12].toInt()
        val bits = BitReader(read(channel, mapOffset + 16, mapBytes))
        val codeLengths = IntArray(16) { bits.read(4) }
        val codes = canonicalCodes(codeLengths)
        val types = IntArray(hunkCount) { decodeSymbol(bits, codeLengths, codes) }
        val selfBits = mapHeader[13].toInt()
        var offset = mapHeader.long(4) shr 16
        val sha = MessageDigest.getInstance("SHA-1")
        val offsets = LongArray(hunkCount)
        val lengths = IntArray(hunkCount)
        val crcs = IntArray(hunkCount)
        var remaining = logical
        fun decode(i: Int): ByteArray {
            val stored = read(channel, offsets[i], lengths[i])
            return when {
                types[i] == Chd.TYPE_NONE -> stored
                codec == Chd.CODEC_CDZL -> inflateCd(stored, hunkBytes)
                codec == Chd.CODEC_ZLIB -> inflate(stored, 0, stored.size, hunkBytes)
                else -> throw IOException("unsupported codec")
            }
        }
        for (i in 0 until hunkCount) {
            if (isCancelled()) throw CancelledException()
            val hunk = when (val type = types[i]) {
                Chd.TYPE_SELF -> {
                    val ref = bits.read(selfBits)
                    if (ref >= i || types[ref] == Chd.TYPE_SELF) throw IOException("hunk $i refers to a bad hunk")
                    decode(ref).also { if (Chd.crc16(it, 0, hunkBytes) != crcs[ref]) throw IOException("hunk $ref is damaged") }
                }
                Chd.TYPE_CODEC0, Chd.TYPE_NONE -> {
                    lengths[i] = if (type == Chd.TYPE_CODEC0) bits.read(lengthBits) else hunkBytes
                    crcs[i] = bits.read(16)
                    offsets[i] = offset
                    offset += lengths[i]
                    decode(i).also { if (Chd.crc16(it, 0, hunkBytes) != crcs[i]) throw IOException("hunk $i is damaged") }
                }
                else -> throw IOException("unsupported hunk type $type")
            }
            val use = minOf(hunkBytes.toLong(), remaining).toInt()
            sha.update(hunk, 0, use)
            remaining -= use
            if (i % 256 == 0) onProgress(logical - remaining)
        }
        val actual = sha.digest()
        if (!actual.contentEquals(expectedSha1)) throw IOException("data checksum doesn't match")
        return Report(actual, logical)
    }

    private fun inflateCd(src: ByteArray, hunkBytes: Int): ByteArray {
        val frames = hunkBytes / Chd.CD_FRAME
        val eccBytes = (frames + 7) / 8
        val baseLength = ((src[eccBytes].toInt() and 0xFF) shl 8) or (src[eccBytes + 1].toInt() and 0xFF)
        val start = eccBytes + 2
        val sectors = inflate(src, start, baseLength, frames * Chd.CD_SECTOR)
        val subcode = inflate(src, start + baseLength, src.size - start - baseLength, frames * Chd.CD_SUBCODE)
        val out = ByteArray(hunkBytes)
        for (f in 0 until frames) {
            System.arraycopy(sectors, f * Chd.CD_SECTOR, out, f * Chd.CD_FRAME, Chd.CD_SECTOR)
            System.arraycopy(subcode, f * Chd.CD_SUBCODE, out, f * Chd.CD_FRAME + Chd.CD_SECTOR, Chd.CD_SUBCODE)
            if ((src[f / 8].toInt() shr (f % 8)) and 1 != 0) CdEcc.generate(out, f * Chd.CD_FRAME)
        }
        return out
    }

    private fun inflate(src: ByteArray, from: Int, len: Int, size: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            // Raw inflate in Java may need one extra byte past the end of the data.
            inflater.setInput(src.copyOfRange(from, from + len) + 0)
            val out = ByteArray(size)
            var n = 0
            while (n < size && !inflater.finished()) {
                val r = inflater.inflate(out, n, size - n)
                if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                n += r
            }
            if (n != size) throw IOException("hunk didn't decompress to the right size")
            return out
        } finally {
            inflater.end()
        }
    }

    private fun canonicalCodes(lengths: IntArray): IntArray {
        val histogram = IntArray(33)
        lengths.forEach { histogram[it]++ }
        var start = 0
        for (len in 32 downTo 1) {
            val next = (start + histogram[len]) shr 1
            histogram[len] = start
            start = next
        }
        return IntArray(lengths.size) { if (lengths[it] > 0) histogram[lengths[it]]++ else -1 }
    }

    private fun decodeSymbol(bits: BitReader, lengths: IntArray, codes: IntArray): Int {
        var code = 0
        for (len in 1..16) {
            code = (code shl 1) or bits.read(1)
            for (s in lengths.indices) if (lengths[s] == len && codes[s] == code) return s
        }
        throw IOException("bad map")
    }

    private fun read(channel: SeekableByteChannel, at: Long, len: Int): ByteArray {
        val buf = ByteBuffer.allocate(len)
        channel.position(at)
        while (buf.hasRemaining()) if (channel.read(buf) < 0) throw EOFException("CHD file is truncated")
        return buf.array()
    }

    private fun ByteArray.int(at: Int) = ByteBuffer.wrap(this, at, 4).int
    private fun ByteArray.long(at: Int) = ByteBuffer.wrap(this, at, 8).long

    private class BitReader(private val data: ByteArray) {
        private var pos = 0L
        fun read(bits: Int): Int {
            var v = 0
            repeat(bits) {
                val byte = (pos ushr 3).toInt()
                val bit = if (byte < data.size) (data[byte].toInt() shr (7 - (pos and 7).toInt())) and 1 else 0
                v = (v shl 1) or bit
                pos++
            }
            return v
        }
    }
}

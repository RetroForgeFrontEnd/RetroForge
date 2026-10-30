package com.retroforge.library.manage

import com.github.luben.zstd.Zstd
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.TreeMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * RVZ, Dolphin's compressed disc format, for GameCube discs. Follows Dolphin's converter (DiscIO/WIABlob.cpp,
 * GPL-2.0-or-later) and docs/WiaAndRvz.md with its defaults: Zstandard level 5, 128 KiB groups. Padding ("junk")
 * that the disc's random generator made is stored as its seed, which is where most of the saving comes from.
 *
 * Wii discs aren't written: their partitions are encrypted, and RVZ stores them decrypted, which needs Nintendo's
 * key; RetroForge doesn't ship it.
 */
object Rvz {
    const val CHUNK = 0x20000
    const val LEVEL = 5
    internal const val BLOCK = 0x8000
    internal const val HEADER_1 = 0x48
    internal const val HEADER_2 = 0xDC
    internal const val DISC_HEADER = 0x80
    private const val RAW_ENTRY = 24
    private const val GROUP_ENTRY = 12
    private const val COMPRESSION_ZSTD = 5

    /** True when [header] (at least 0x20 bytes from the start of an ISO) is a GameCube disc. */
    fun isGameCube(header: ByteArray): Boolean = header.size >= 0x20 && ByteBuffer.wrap(header, 0x1C, 4).int == 0xC2339F3D.toInt()

    /**
     * Writes [input] (a GameCube ISO of [isoBytes]) as RVZ to [out] (positioned at 0, empty). Groups are packed and
     * compressed on all cores; [onProgress] gets bytes consumed. Returns the size of the file.
     */
    fun write(
        out: SeekableByteChannel,
        input: SeekableByteChannel,
        isoBytes: Long,
        onProgress: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Long {
        if (isoBytes < 0x440) throw IOException("too small to be a GameCube disc")
        val discHeader = read(input, 0, 0x440)
        if (!isGameCube(discHeader)) throw IOException("not a GameCube disc")
        val files = Files.read(input, discHeader, isoBytes)

        val groupCount = ((isoBytes + CHUNK - 1) / CHUNK).toInt()
        val groups = ByteArray(groupCount * GROUP_ENTRY)
        var offset = (HEADER_1 + HEADER_2).toLong()
        out.position(0)
        writeFully(out, ByteBuffer.allocate(offset.toInt()))
        input.position(0)
        val stream = Channels.newInputStream(input)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            var group = 0
            var consumed = 0L
            val batch = threads * 4
            while (group < groupCount) {
                if (isCancelled()) throw CancelledException()
                val inputs = ArrayList<ByteArray>(batch)
                while (inputs.size < batch && group + inputs.size < groupCount) {
                    val want = minOf(CHUNK.toLong(), isoBytes - consumed).toInt()
                    val buf = ByteArray(want)
                    Chd.readFully(stream, buf, want)
                    consumed += want
                    inputs += buf
                }
                val first = group
                val packed = pool.invokeAll(inputs.mapIndexed { i, buf -> Callable { encodeGroup(buf, (first + i).toLong() * CHUNK, files) } }).map { it.get() }
                for ((i, g) in packed.withIndex()) {
                    val e = ByteBuffer.wrap(groups, (group + i) * GROUP_ENTRY, GROUP_ENTRY)
                    if (g == null) {
                        e.putInt(0).putInt(0).putInt(0)
                        continue
                    }
                    e.putInt((offset ushr 2).toInt())
                    e.putInt(g.data.size or (if (g.compressed) Int.MIN_VALUE else 0))
                    e.putInt(g.packedSize)
                    writeFully(out, ByteBuffer.wrap(g.data))
                    offset += g.data.size
                    offset = pad4(out, offset)
                }
                group += inputs.size
                onProgress(consumed)
            }
        } finally {
            pool.shutdownNow()
        }

        val raw = ByteBuffer.allocate(RAW_ENTRY)
            .putLong(DISC_HEADER.toLong()).putLong(isoBytes - DISC_HEADER).putInt(0).putInt(groupCount).array()
        val rawPacked = Zstd.compress(raw, LEVEL)
        val rawOffset = offset
        writeFully(out, ByteBuffer.wrap(rawPacked))
        offset = pad4(out, offset + rawPacked.size)
        val groupsPacked = Zstd.compress(groups, LEVEL)
        val groupsOffset = offset
        writeFully(out, ByteBuffer.wrap(groupsPacked))
        offset = pad4(out, offset + groupsPacked.size)

        val h2 = ByteBuffer.allocate(HEADER_2)
        h2.putInt(1).putInt(COMPRESSION_ZSTD).putInt(LEVEL).putInt(CHUNK)
        h2.put(discHeader, 0, DISC_HEADER)
        h2.putInt(0).putInt(0x30).putLong(HEADER_1.toLong() + HEADER_2)
        h2.put(MessageDigest.getInstance("SHA-1").digest())
        h2.putInt(1).putLong(rawOffset).putInt(rawPacked.size)
        h2.putInt(groupCount).putLong(groupsOffset).putInt(groupsPacked.size)
        // compr_data_len and compr_data stay 0: Zstandard has no codec properties.
        val h2Bytes = h2.array()
        val h1 = ByteBuffer.allocate(HEADER_1)
        h1.put("RVZ".toByteArray(Charsets.US_ASCII)).put(1.toByte())
        h1.putInt(0x01000000).putInt(0x00030000).putInt(HEADER_2)
        h1.put(MessageDigest.getInstance("SHA-1").digest(h2Bytes))
        h1.putLong(isoBytes).putLong(offset)
        h1.put(MessageDigest.getInstance("SHA-1").digest(h1.array().copyOf(0x34)))
        out.position(0)
        writeFully(out, ByteBuffer.wrap(h1.array()))
        writeFully(out, ByteBuffer.wrap(h2Bytes))
        return offset
    }

    /** A group as stored: [packedSize] is 0 when RVZ packing wasn't used. */
    internal class Group(val data: ByteArray, val compressed: Boolean, val packedSize: Int)

    /** Null for a group of zeros, which RVZ stores as nothing. */
    internal fun encodeGroup(data: ByteArray, discOffset: Long, files: Files): Group? {
        if (data.all { it.toInt() == 0 }) return null
        val packed = pack(data, discOffset, files)
        val main = packed ?: data
        val z = Zstd.compress(main, LEVEL)
        return if (z.size < main.size) Group(z, true, packed?.size ?: 0) else Group(main, false, packed?.size ?: 0)
    }

    private class Junk(val start: Int, val seed: IntArray)

    /** Dolphin's RVZPack for one group; null when the group has no junk worth storing as a seed. */
    internal fun pack(data: ByteArray, discOffset: Long, files: Files): ByteArray? {
        val total = data.size
        val junk = TreeMap<Int, Junk>()
        var position = 0
        var at = discOffset
        while (position < total) {
            // Skip the 0 to 32 zero bytes that typically come after a file.
            while (position < total && data[position].toInt() == 0) { position++; at++ }
            if (position >= total) break
            val toBlockEnd = (((at + 1) + BLOCK - 1) / BLOCK * BLOCK - at).toInt()
            val toRead = minOf(toBlockEnd, total - position)
            val seed = IntArray(Lfg.SEED_WORDS)
            val found = Lfg.findSeed(data, position, toRead, (at % BLOCK).toInt(), seed)
            if (found > 0) junk.putIfAbsent(position + found, Junk(position, seed))
            // A file that ends inside this block: carry on looking right after it.
            val fileEnd = files.endOfFileAt(at + found)
            if (fileEnd != null && fileEnd < at + toRead) {
                position += (fileEnd - at).toInt()
                at = fileEnd
                continue
            }
            position += toRead
            at += toRead
        }

        val out = ByteArrayOutputStream(total / 2)
        var current = 0
        var first = true
        while (current < total) {
            var junkStart = total
            var junkEnd = total
            var seed: IntArray? = null
            if (total - current > Lfg.SEED_BYTES) {
                val next = junk.higherEntry(current + Lfg.SEED_BYTES)
                if (next != null && next.value.start + Lfg.SEED_BYTES < total) {
                    junkStart = maxOf(current, next.value.start)
                    junkEnd = minOf(total, next.key)
                    seed = next.value.seed
                }
            }
            if (first) {
                if (junkStart == total) return null
                first = false
            }
            val literal = junkStart - current
            if (literal > 0) {
                out.writeInt(literal)
                out.write(data, current, literal)
                current += literal
            }
            val run = junkEnd - current
            if (run > 0) {
                out.writeInt(run or Int.MIN_VALUE)
                for (w in seed!!) out.writeInt(w)
                current += run
            }
        }
        return out.toByteArray()
    }

    /** Undoes [pack] for a group starting at [discOffset]. */
    internal fun unpack(packed: ByteArray, size: Int, discOffset: Long): ByteArray {
        val out = ByteArray(size)
        var p = 0
        var n = 0
        val seed = IntArray(Lfg.SEED_WORDS)
        while (p < packed.size) {
            if (p + 4 > packed.size) throw IOException("packed group is cut short")
            val word = ByteBuffer.wrap(packed, p, 4).int
            p += 4
            val len = word and Int.MAX_VALUE
            if (n + len > size) throw IOException("packed group is too long")
            if (word < 0) {
                if (p + Lfg.SEED_BYTES > packed.size) throw IOException("packed group is cut short")
                for (i in seed.indices) seed[i] = ByteBuffer.wrap(packed, p + 4 * i, 4).int
                p += Lfg.SEED_BYTES
                val lfg = Lfg()
                lfg.setSeed(seed)
                lfg.skip((discOffset + n) % BLOCK)
                lfg.fill(out, n, len)
            } else {
                if (p + len > packed.size) throw IOException("packed group is cut short")
                System.arraycopy(packed, p, out, n, len)
                p += len
            }
            n += len
        }
        if (n != size) throw IOException("packed group is too short")
        return out
    }

    /** The files of a GameCube disc, from its file table, so junk search can skip to where each file ends. */
    internal class Files(private val starts: LongArray, private val ends: LongArray) {
        /** The end of the file holding [offset], or null between files. */
        fun endOfFileAt(offset: Long): Long? {
            var lo = 0
            var hi = starts.size - 1
            var best = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (starts[mid] <= offset) { best = mid; lo = mid + 1 } else hi = mid - 1
            }
            return if (best >= 0 && offset < ends[best]) ends[best] else null
        }

        companion object {
            val NONE = Files(LongArray(0), LongArray(0))

            fun read(input: SeekableByteChannel, header: ByteArray, isoBytes: Long): Files {
                val fstOffset = ByteBuffer.wrap(header, 0x424, 4).int.toLong() and 0xFFFFFFFFL
                val fstSize = ByteBuffer.wrap(header, 0x428, 4).int.toLong() and 0xFFFFFFFFL
                if (fstSize < 12 || fstSize > 64L shl 20 || fstOffset + fstSize > isoBytes) return NONE
                val fst = Rvz.read(input, fstOffset, fstSize.toInt())
                val count = ByteBuffer.wrap(fst, 8, 4).int.toLong() and 0xFFFFFFFFL
                if (count * 12 > fst.size) return NONE
                val list = ArrayList<Pair<Long, Long>>()
                for (i in 1 until count.toInt()) {
                    val e = i * 12
                    if (fst[e].toInt() != 0) continue
                    val start = ByteBuffer.wrap(fst, e + 4, 4).int.toLong() and 0xFFFFFFFFL
                    val size = ByteBuffer.wrap(fst, e + 8, 4).int.toLong() and 0xFFFFFFFFL
                    if (size > 0) list += start to start + size
                }
                list.sortBy { it.first }
                return Files(LongArray(list.size) { list[it].first }, LongArray(list.size) { list[it].second })
            }
        }
    }

    private fun ByteArrayOutputStream.writeInt(v: Int) {
        write(v ushr 24); write(v ushr 16); write(v ushr 8); write(v)
    }

    private fun pad4(out: SeekableByteChannel, offset: Long): Long {
        val pad = ((4 - (offset and 3)) and 3).toInt()
        if (pad > 0) writeFully(out, ByteBuffer.allocate(pad))
        return offset + pad
    }

    internal fun read(channel: SeekableByteChannel, at: Long, len: Int): ByteArray {
        val buf = ByteBuffer.allocate(len)
        channel.position(at)
        while (buf.hasRemaining()) if (channel.read(buf) < 0) throw EOFException("the file ended early")
        return buf.array()
    }

    private fun writeFully(out: SeekableByteChannel, buf: ByteBuffer) {
        while (buf.hasRemaining()) out.write(buf)
    }
}

/**
 * Reads back the RVZ files RetroForge writes (GameCube, Zstandard or stored groups) and compares every byte with the
 * original disc before the original is moved aside. Not a general RVZ reader: Wii discs and other codecs are refused.
 */
object RvzVerifier {

    fun verify(rvz: SeekableByteChannel, original: InputStream, onProgress: (Long) -> Unit = {}, isCancelled: () -> Boolean = { false }) {
        val h1 = Rvz.read(rvz, 0, Rvz.HEADER_1)
        if (String(h1, 0, 4, Charsets.ISO_8859_1) != "RVZ\u0001") throw IOException("not an RVZ file")
        val sha = MessageDigest.getInstance("SHA-1")
        if (!sha.digest(h1.copyOf(0x34)).contentEquals(h1.copyOfRange(0x34, 0x48))) throw IOException("RVZ header is damaged")
        val h2 = Rvz.read(rvz, Rvz.HEADER_1.toLong(), Rvz.HEADER_2)
        if (!sha.digest(h2).contentEquals(h1.copyOfRange(0x10, 0x24))) throw IOException("RVZ header is damaged")
        val b1 = ByteBuffer.wrap(h1)
        val b2 = ByteBuffer.wrap(h2)
        val isoBytes = b1.getLong(0x24)
        if (b1.getLong(0x2C) != rvz.size()) throw IOException("RVZ file is the wrong size")
        if (b2.getInt(0) != 1 || b2.getInt(4) != 5 || b2.getInt(0x90) != 0) throw IOException("not a GameCube RVZ RetroForge can check")
        val chunk = b2.getInt(0x0C)
        if (b2.getInt(0xB4) != 1) throw IOException("unexpected RVZ layout")
        val raw = ByteBuffer.wrap(Zstd.decompress(Rvz.read(rvz, b2.getLong(0xB8), b2.getInt(0xC0)), 24))
        val groupCount = b2.getInt(0xC4)
        if (raw.getLong(0) != Rvz.DISC_HEADER.toLong() || raw.getLong(8) != isoBytes - Rvz.DISC_HEADER || raw.getInt(16) != 0 || raw.getInt(20) != groupCount) {
            throw IOException("unexpected RVZ layout")
        }
        val groups = ByteBuffer.wrap(Zstd.decompress(Rvz.read(rvz, b2.getLong(0xC8), b2.getInt(0xD0)), groupCount * 12))
        val expected = ByteArray(chunk)
        var done = 0L
        for (i in 0 until groupCount) {
            if (isCancelled()) throw CancelledException()
            val size = minOf(chunk.toLong(), isoBytes - done).toInt()
            Chd.readFully(original, expected, size)
            val dataOffset = (groups.getInt(i * 12).toLong() and 0xFFFFFFFFL) shl 2
            val dataSize = groups.getInt(i * 12 + 4)
            val packedSize = groups.getInt(i * 12 + 8)
            val stored = dataSize and Int.MAX_VALUE
            val decoded = if (stored == 0) {
                ByteArray(size)
            } else {
                val bytes = Rvz.read(rvz, dataOffset, stored)
                val main = if (dataSize < 0) Zstd.decompress(bytes, if (packedSize != 0) packedSize else size) else bytes
                if (packedSize != 0) Rvz.unpack(main.copyOf(packedSize), size, done) else main
            }
            if (decoded.size != size || !decoded.contentEquals(expected.copyOf(size))) throw IOException("group $i doesn't match the original")
            done += size
            if (i % 64 == 0) onProgress(done)
        }
        if (original.read() != -1) throw IOException("the original is longer than the RVZ")
        onProgress(done)
    }
}

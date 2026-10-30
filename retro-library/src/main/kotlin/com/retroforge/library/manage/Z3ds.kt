package com.retroforge.library.manage

import com.github.luben.zstd.Zstd
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Z3DS, Azahar's compressed 3DS format (.zcci, .zcxi, .z3dsx). Follows Azahar's writer (src/common/file_derived.cpp
 * and zstd_compression.cpp, GPL-3.0-or-later): a 0x20-byte header, a small metadata block, then the original file
 * as a Zstandard seekable stream of 256 KiB frames (level 3, no checksums) ending in its seek table.
 */
object Z3ds {
    const val FRAME = 256 * 1024
    const val LEVEL = 3
    internal const val HEADER = 0x20
    private const val SKIPPABLE_MAGIC = 0x184D2A5E
    private const val SEEKABLE_MAGIC = 0x8F92EAB1.toInt()

    /** What the file holds, its Azahar magic and the extension Azahar gives it once compressed. */
    enum class Kind(val magic: String, val extension: String) {
        NCSD("NCSD", "zcci"),
        NCCH("NCCH", "zcxi"),
        THREEDSX("3DSX", "z3dsx"),
    }

    /** The kind of a 3DS file from its first 0x200 bytes, or null when it isn't one Azahar compresses. */
    fun kindOf(header: ByteArray): Kind? = when {
        header.size >= 4 && String(header, 0, 4, Charsets.ISO_8859_1) == "3DSX" -> Kind.THREEDSX
        header.size >= 0x104 && String(header, 0x100, 4, Charsets.ISO_8859_1) == "NCSD" -> Kind.NCSD
        header.size >= 0x104 && String(header, 0x100, 4, Charsets.ISO_8859_1) == "NCCH" -> Kind.NCCH
        else -> null
    }

    /**
     * Writes [input] ([bytes] long, of [kind]) as Z3DS to [out] (positioned at 0, empty). Frames are compressed on all
     * cores; [onProgress] gets bytes consumed. Returns the size of the file.
     */
    fun write(
        out: SeekableByteChannel,
        input: SeekableByteChannel,
        bytes: Long,
        kind: Kind,
        onProgress: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Long {
        val metadata = metadata(titleInfo(input, kind))
        out.position(0)
        writeFully(out, ByteBuffer.allocate(HEADER))
        writeFully(out, ByteBuffer.wrap(metadata))
        val frameCount = ((bytes + FRAME - 1) / FRAME).toInt()
        val table = ByteBuffer.allocate(frameCount * 8).order(ByteOrder.LITTLE_ENDIAN)
        var compressed = 0L
        input.position(0)
        val stream = java.nio.channels.Channels.newInputStream(input)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            var frame = 0
            var consumed = 0L
            val batch = threads * 8
            while (frame < frameCount) {
                if (isCancelled()) throw CancelledException()
                val inputs = ArrayList<ByteArray>(batch)
                while (inputs.size < batch && frame + inputs.size < frameCount) {
                    val want = minOf(FRAME.toLong(), bytes - consumed).toInt()
                    val buf = ByteArray(want)
                    Chd.readFully(stream, buf, want)
                    consumed += want
                    inputs += buf
                }
                val frames = pool.invokeAll(inputs.map { buf -> Callable { Zstd.compress(buf, LEVEL) } }).map { it.get() }
                for ((i, z) in frames.withIndex()) {
                    table.putInt(z.size).putInt(inputs[i].size)
                    writeFully(out, ByteBuffer.wrap(z))
                    compressed += z.size
                }
                frame += inputs.size
                onProgress(consumed)
            }
        } finally {
            pool.shutdownNow()
        }

        // The seek table: a skippable frame of (compressed, decompressed) sizes, then frame count, flags, magic.
        val seek = ByteBuffer.allocate(8 + frameCount * 8 + 9).order(ByteOrder.LITTLE_ENDIAN)
        seek.putInt(SKIPPABLE_MAGIC).putInt(frameCount * 8 + 9)
        seek.put(table.array())
        seek.putInt(frameCount).put(0.toByte()).putInt(SEEKABLE_MAGIC)
        writeFully(out, ByteBuffer.wrap(seek.array()))
        compressed += seek.capacity()

        val header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
        header.put("Z3DS".toByteArray(Charsets.US_ASCII)).put(kind.magic.toByteArray(Charsets.US_ASCII))
        header.put(1.toByte()).put(0.toByte()).putShort(HEADER.toShort()).putInt(metadata.size)
        header.putLong(compressed).putLong(bytes)
        out.position(0)
        writeFully(out, ByteBuffer.wrap(header.array()))
        return HEADER + metadata.size + compressed
    }

    /** Azahar's metadata block: version 1, then (type 1, name length, data length, name, data) items, an end item, padded to 16. */
    internal fun metadata(titleInfo: ByteArray?): ByteArray {
        val items = linkedMapOf(
            "compressor" to "RetroForge".toByteArray(),
            "date" to DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()).toByteArray(),
            "maxframesize" to FRAME.toString().toByteArray(),
        )
        if (titleInfo != null) items["titleinfo"] = titleInfo
        val out = ByteArrayOutputStream()
        out.write(1)
        for ((name, data) in items) {
            out.write(1)
            out.write(name.length)
            out.write(data.size and 0xFF)
            out.write(data.size ushr 8)
            out.write(name.toByteArray(Charsets.US_ASCII))
            out.write(data)
        }
        out.write(ByteArray(4))
        val bytes = out.toByteArray()
        return bytes.copyOf((bytes.size + 15) / 16 * 16)
    }

    /**
     * Azahar's "titleinfo" for cartridge and NCCH images: program ID, content size and version from the first NCCH
     * header (Service::AM::TitleInfo, 0x18 bytes, little endian). Null for 3DSX or when the header can't be read.
     */
    private fun titleInfo(input: SeekableByteChannel, kind: Kind): ByteArray? = runCatching {
        val ncchAt = when (kind) {
            Kind.NCSD -> (ByteBuffer.wrap(Rvz.read(input, 0x120, 4)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL) * 0x200
            Kind.NCCH -> 0L
            Kind.THREEDSX -> return null
        }
        val ncch = ByteBuffer.wrap(Rvz.read(input, ncchAt, 0x200)).order(ByteOrder.LITTLE_ENDIAN)
        if (String(ncch.array(), 0x100, 4, Charsets.ISO_8859_1) != "NCCH") return null
        val unit = 0x200L shl (ncch.get(0x18E).toInt() and 0xFF)
        ByteBuffer.allocate(0x18).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(ncch.getLong(0x118))
            .putLong((ncch.getInt(0x104).toLong() and 0xFFFFFFFFL) * unit)
            .putShort(ncch.getShort(0x112)).putShort(0).putInt(0)
            .array()
    }.getOrNull()

    private fun writeFully(out: SeekableByteChannel, buf: ByteBuffer) {
        while (buf.hasRemaining()) out.write(buf)
    }
}

/**
 * Reads back the Z3DS files RetroForge writes and compares every frame with the original before it's moved aside.
 */
object Z3dsVerifier {

    fun verify(z3ds: SeekableByteChannel, original: InputStream, onProgress: (Long) -> Unit = {}, isCancelled: () -> Boolean = { false }) {
        val header = ByteBuffer.wrap(Rvz.read(z3ds, 0, Z3ds.HEADER)).order(ByteOrder.LITTLE_ENDIAN)
        if (String(header.array(), 0, 4, Charsets.ISO_8859_1) != "Z3DS" || header.get(8).toInt() != 1) throw IOException("not a Z3DS file")
        val start = (header.getShort(10).toLong() and 0xFFFF) + (header.getInt(12).toLong() and 0xFFFFFFFFL)
        val compressed = header.getLong(16)
        val bytes = header.getLong(24)
        if (start + compressed != z3ds.size()) throw IOException("Z3DS file is the wrong size")
        val footer = ByteBuffer.wrap(Rvz.read(z3ds, z3ds.size() - 9, 9)).order(ByteOrder.LITTLE_ENDIAN)
        val frames = footer.getInt(0)
        if (footer.getInt(5) != 0x8F92EAB1.toInt() || footer.get(4).toInt() != 0) throw IOException("Z3DS seek table is damaged")
        val tableAt = z3ds.size() - 9 - frames * 8L
        val table = ByteBuffer.wrap(Rvz.read(z3ds, tableAt, frames * 8)).order(ByteOrder.LITTLE_ENDIAN)
        val expected = ByteArray(Z3ds.FRAME)
        var at = start
        var done = 0L
        for (i in 0 until frames) {
            if (isCancelled()) throw CancelledException()
            val csize = table.getInt(i * 8)
            val dsize = table.getInt(i * 8 + 4)
            val frame = Zstd.decompress(Rvz.read(z3ds, at, csize), dsize)
            at += csize
            Chd.readFully(original, expected, dsize)
            if (frame.size != dsize || !frame.contentEquals(expected.copyOf(dsize))) throw IOException("frame $i doesn't match the original")
            done += dsize
            if (i % 256 == 0) onProgress(done)
        }
        if (done != bytes || at != tableAt - 8) throw IOException("Z3DS sizes don't add up")
        if (original.read() != -1) throw IOException("the original is longer than the Z3DS")
        onProgress(done)
    }
}

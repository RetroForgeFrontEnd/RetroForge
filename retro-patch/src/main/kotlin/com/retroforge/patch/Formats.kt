package com.retroforge.patch

/** IPS: no checksums, 24-bit offsets, optional RLE records and an optional truncation size after "EOF". */
internal object Ips {
    fun apply(source: ByteArray, patch: ByteArray): ByteArray {
        val reader = PatchReader(patch)
        if (!reader.matches("PATCH")) malformed("missing IPS header")
        reader.skip(5)
        var output = source.copyOf()
        var size = source.size

        fun ensure(newSize: Int) {
            if (newSize.toLong() > MAX_OUTPUT_BYTES) fail(PatchError.TooLarge(newSize.toLong()))
            if (newSize > output.size) output = output.copyOf(maxOf(newSize, output.size * 2).coerceAtMost(MAX_OUTPUT_BYTES.toInt()))
            if (newSize > size) size = newSize
        }

        while (true) {
            if (reader.matches("EOF")) {
                reader.skip(3)
                if (reader.remaining >= 3) {
                    val truncateTo = reader.u24be()
                    if (truncateTo < size) size = truncateTo
                }
                return output.copyOf(size)
            }
            val offset = reader.u24be()
            val length = reader.u16be()
            if (length == 0) {
                val runLength = reader.u16be()
                val value = reader.u8().toByte()
                ensure(offset + runLength)
                output.fill(value, offset, offset + runLength)
            } else {
                val bytes = reader.bytes(length)
                ensure(offset + length)
                bytes.copyInto(output, offset)
            }
        }
    }
}

/** Header fields shared by UPS and BPS, read before applying so the UI can check the ROM first. */
data class ChecksummedHeader(
    val sourceSize: Long,
    val targetSize: Long,
    val sourceCrc32: Long,
    val targetCrc32: Long,
    val metadata: String?,
)

internal abstract class ChecksummedFormat(private val magic: String) {

    fun header(patch: ByteArray): ChecksummedHeader {
        val reader = open(patch)
        val sourceSize = reader.varint()
        val targetSize = reader.varint()
        val metadata = readMetadata(reader)
        return ChecksummedHeader(sourceSize, targetSize, readLe32(patch, patch.size - 12), readLe32(patch, patch.size - 8), metadata)
    }

    protected open fun readMetadata(reader: PatchReader): String? = null

    protected fun open(patch: ByteArray): PatchReader {
        if (patch.size < magic.length + 12 || !startsWith(patch, magic)) malformed("missing $magic header")
        if (crc32(patch, patch.size - 4) != readLe32(patch, patch.size - 4)) fail(PatchError.PatchDamaged)
        return PatchReader(patch, patch.size - 12).also { it.skip(magic.length) }
    }

    protected fun verifySource(source: ByteArray, header: ChecksummedHeader) {
        val actual = crc32(source)
        if (source.size.toLong() != header.sourceSize || actual != header.sourceCrc32) {
            fail(PatchError.WrongSourceRom(header.sourceCrc32, actual, header.sourceSize))
        }
    }

    protected fun verifyTarget(target: ByteArray, header: ChecksummedHeader) {
        if (crc32(target) != header.targetCrc32) fail(PatchError.OutputMismatch)
    }

    abstract fun apply(source: ByteArray, patch: ByteArray): ByteArray
}

/** UPS: XOR hunks at relative offsets. Only the forward direction (source → target) is supported. */
internal object Ups : ChecksummedFormat("UPS1") {
    override fun apply(source: ByteArray, patch: ByteArray): ByteArray {
        val header = header(patch)
        verifySource(source, header)
        val reader = open(patch)
        reader.varint()
        val targetSize = checkedSize(reader.varint())
        val target = source.copyOf(targetSize)
        var offset = 0L
        while (reader.remaining > 0) {
            offset += reader.varint()
            while (true) {
                val xor = reader.u8()
                if (offset < targetSize) {
                    val index = offset.toInt()
                    target[index] = (target[index].toInt() xor xor).toByte()
                }
                offset++
                if (xor == 0) break
            }
        }
        verifyTarget(target, header)
        return target
    }
}

/** BPS: copy/insert commands with relative source and target pointers. */
internal object Bps : ChecksummedFormat("BPS1") {

    override fun readMetadata(reader: PatchReader): String? {
        val length = checkedSize(reader.varint())
        return if (length == 0) null else reader.bytes(length).toString(Charsets.UTF_8)
    }

    override fun apply(source: ByteArray, patch: ByteArray): ByteArray {
        val header = header(patch)
        verifySource(source, header)
        val reader = open(patch)
        reader.varint()
        val target = ByteArray(checkedSize(reader.varint()))
        reader.skip(checkedSize(reader.varint()))

        var out = 0
        var sourceRelative = 0L
        var targetRelative = 0L
        while (reader.remaining > 0) {
            val data = reader.varint()
            val length = checkedSize((data shr 2) + 1)
            if (out.toLong() + length > target.size) malformed("command writes past the end of the output")
            when ((data and 3).toInt()) {
                0 -> {
                    if (out + length > source.size) malformed("source read past the end of the ROM")
                    source.copyInto(target, out, out, out + length)
                    out += length
                }
                1 -> {
                    reader.bytes(length).copyInto(target, out)
                    out += length
                }
                2 -> {
                    sourceRelative += signedOffset(reader.varint())
                    if (sourceRelative < 0 || sourceRelative + length > source.size) malformed("source copy out of range")
                    source.copyInto(target, out, sourceRelative.toInt(), sourceRelative.toInt() + length)
                    sourceRelative += length
                    out += length
                }
                else -> {
                    targetRelative += signedOffset(reader.varint())
                    repeat(length) {
                        // Byte-by-byte on purpose: target copies may overlap the bytes being written (run-length style).
                        if (targetRelative < 0 || targetRelative >= out) malformed("target copy out of range")
                        target[out++] = target[targetRelative.toInt()]
                        targetRelative++
                    }
                }
            }
        }
        verifyTarget(target, header)
        return target
    }

    private fun signedOffset(encoded: Long): Long = if (encoded and 1L != 0L) -(encoded shr 1) else encoded shr 1
}

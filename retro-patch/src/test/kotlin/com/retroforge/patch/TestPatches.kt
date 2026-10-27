package com.retroforge.patch

import java.io.ByteArrayOutputStream

/** Minimal reference encoders used only to build test patches. */
object TestPatches {

    fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    fun ips(vararg records: Pair<Int, ByteArray>, rle: List<Triple<Int, Int, Int>> = emptyList(), truncateTo: Int? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("PATCH".toByteArray())
        for ((offset, data) in records) {
            out.u24(offset); out.u16(data.size); out.write(data)
        }
        for ((offset, count, value) in rle) {
            out.u24(offset); out.u16(0); out.u16(count); out.write(value)
        }
        out.write("EOF".toByteArray())
        truncateTo?.let { out.u24(it) }
        return out.toByteArray()
    }

    fun ups(source: ByteArray, target: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("UPS1".toByteArray())
        out.varint(source.size.toLong()); out.varint(target.size.toLong())
        fun s(i: Int) = if (i < source.size) source[i].toInt() and 0xFF else 0
        var last = 0
        var i = 0
        while (i < target.size) {
            if (s(i) xor (target[i].toInt() and 0xFF) == 0) { i++; continue }
            out.varint((i - last).toLong())
            while (i < target.size) {
                val x = s(i) xor (target[i].toInt() and 0xFF)
                if (x == 0) break
                out.write(x); i++
            }
            out.write(0); i++
            last = i
        }
        return out.withFooter(source, target)
    }

    sealed interface Command {
        data class SourceRead(val length: Int) : Command
        data class TargetRead(val data: ByteArray) : Command
        data class SourceCopy(val delta: Long, val length: Int) : Command
        data class TargetCopy(val delta: Long, val length: Int) : Command
    }

    fun bps(source: ByteArray, target: ByteArray, commands: List<Command>, metadata: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("BPS1".toByteArray())
        out.varint(source.size.toLong()); out.varint(target.size.toLong())
        val meta = metadata.toByteArray()
        out.varint(meta.size.toLong()); out.write(meta)
        for (command in commands) {
            when (command) {
                is Command.SourceRead -> out.varint(((command.length - 1).toLong() shl 2) or 0)
                is Command.TargetRead -> { out.varint(((command.data.size - 1).toLong() shl 2) or 1); out.write(command.data) }
                is Command.SourceCopy -> { out.varint(((command.length - 1).toLong() shl 2) or 2); out.varint(signed(command.delta)) }
                is Command.TargetCopy -> { out.varint(((command.length - 1).toLong() shl 2) or 3); out.varint(signed(command.delta)) }
            }
        }
        return out.withFooter(source, target)
    }

    private fun signed(delta: Long) = if (delta < 0) ((-delta) shl 1) or 1 else delta shl 1

    private fun ByteArrayOutputStream.withFooter(source: ByteArray, target: ByteArray): ByteArray {
        le32(crc32(source)); le32(crc32(target))
        val body = toByteArray()
        le32(crc32(body))
        return toByteArray()
    }

    private fun ByteArrayOutputStream.u24(v: Int) { write(v shr 16); write(v shr 8); write(v) }
    private fun ByteArrayOutputStream.u16(v: Int) { write(v shr 8); write(v) }
    private fun ByteArrayOutputStream.le32(v: Long) { for (i in 0 until 4) write((v shr (8 * i)).toInt()) }

    private fun ByteArrayOutputStream.varint(value: Long) {
        var data = value
        while (true) {
            val x = (data and 0x7F).toInt()
            data = data shr 7
            if (data == 0L) { write(0x80 or x); return }
            write(x)
            data--
        }
    }
}

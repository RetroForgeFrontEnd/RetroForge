package com.retroforge.pcplayer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * The on-screen gamepad as Windows games see it: an Xbox 360 controller. Its state goes into a small shared file
 * that libpcpad.so (preloaded into Wine's controller driver) reads whenever SDL updates its joysticks; the layout
 * is described in pcpad.c.
 */
class VirtualPad(val file: File) {

    enum class Button { A, B, X, Y, BACK, GUIDE, START, LEFT_STICK, RIGHT_STICK, LB, RB, UP, DOWN, LEFT, RIGHT }
    enum class Axis { LEFT_X, LEFT_Y, RIGHT_X, RIGHT_Y, LEFT_TRIGGER, RIGHT_TRIGGER }

    private val buffer: MappedByteBuffer = RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(FILE_SIZE.toLong())
        raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_SIZE.toLong())
    }.apply { order(ByteOrder.LITTLE_ENDIAN) }

    init {
        reset()
    }

    /** Whether Windows sees a controller plugged in. */
    var connected: Boolean
        get() = buffer.getInt(0) != 0
        set(value) { buffer.putInt(0, if (value) 1 else 0) }

    /** Sticks from -1 (left/up) to 1 (right/down); triggers from 0 (released) to 1. */
    fun setAxis(axis: Axis, value: Float) {
        val raw = when (axis) {
            Axis.LEFT_TRIGGER, Axis.RIGHT_TRIGGER -> value.coerceIn(0f, 1f) * 65535f - 32768f
            else -> value.coerceIn(-1f, 1f) * 32767f
        }
        buffer.putShort(4 + axis.ordinal * 2, raw.toInt().toShort())
    }

    fun setButton(button: Button, pressed: Boolean) {
        buffer.put(16 + button.ordinal, if (pressed) 1 else 0)
    }

    /** Everything released and centred. */
    fun reset() {
        Axis.entries.forEach { setAxis(it, 0f) }
        Button.entries.forEach { setButton(it, false) }
    }

    companion object {
        private const val FILE_SIZE = 64
    }
}

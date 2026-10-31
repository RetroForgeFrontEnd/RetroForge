package com.retroforge.app.ui.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Listens to the music on its way to the speaker (it passes through unchanged) and keeps the last moments of it, for
 * the Now playing page's visuals. No microphone or "record audio" permission: it's RetroForge's own sound.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MusicTap : BaseAudioProcessor() {
    const val SIZE = 1024
    private val ring = FloatArray(SIZE)
    private var at = 0
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining()
        if (count == 0) return
        val samples = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        synchronized(ring) {
            // Every channel mixed to one: the visuals don't need stereo.
            var i = 0
            val frames = samples.remaining() / channels
            for (f in 0 until frames) {
                var sum = 0
                for (ch in 0 until channels) sum += samples.get(i++).toInt()
                ring[at] = sum / (channels * 32768f)
                at = (at + 1) % SIZE
            }
        }
        replaceOutputBuffer(count).put(inputBuffer).flip()
    }

    /** The latest [SIZE] samples, oldest first. */
    fun snapshot(out: FloatArray) = synchronized(ring) {
        for (i in 0 until SIZE) out[i] = ring[(at + i) % SIZE]
    }

    /** Silence, when the music stops, so the bars fall instead of freezing. */
    fun clear() = synchronized(ring) { ring.fill(0f) }
}

/** Turns samples into bars: low notes on the left, high on the right, as music visualisers show them. */
object Spectrum {
    /** [bars] heights from 0 to 1 for [samples] (a power of two long). */
    fun bars(samples: FloatArray, bars: Int): FloatArray {
        val n = samples.size
        val re = DoubleArray(n) { samples[it] * (0.5 - 0.5 * cos(2 * PI * it / (n - 1))) } // Hann window
        val im = DoubleArray(n)
        fft(re, im)
        val half = n / 2
        val out = FloatArray(bars)
        // Each bar covers a stretch of notes spaced like a piano's (logarithmic), from the 2nd bin to the top.
        val top = ln(half.toDouble())
        for (b in 0 until bars) {
            val from = (kotlin.math.E.pow(top * b / bars)).toInt().coerceAtLeast(1)
            val to = (kotlin.math.E.pow(top * (b + 1) / bars)).toInt().coerceIn(from + 1, half)
            var peak = 0.0
            for (k in from until to) peak = maxOf(peak, hypot(re[k], im[k]))
            // Decibels, roughly 60 dB of range, so quiet music still shows.
            val db = 20 * kotlin.math.log10(peak / (n / 4) + 1e-9)
            out[b] = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
        }
        return out
    }

    /** In-place radix-2 FFT. */
    internal fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val c = cos(angle * k)
                    val s = sin(angle * k)
                    val a = i + k
                    val b = a + len / 2
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
            }
            len = len shl 1
        }
    }
}

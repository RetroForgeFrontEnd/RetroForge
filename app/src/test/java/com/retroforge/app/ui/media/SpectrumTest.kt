package com.retroforge.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumTest {
    @Test
    fun silenceIsFlat() {
        assertTrue(Spectrum.bars(FloatArray(1024), 16).all { it == 0f })
    }

    @Test
    fun aLowToneLightsTheLeftAndAHighToneTheRight() {
        fun tone(bin: Int) = FloatArray(1024) { (0.8 * sin(2 * PI * bin * it / 1024)).toFloat() }
        val low = Spectrum.bars(tone(4), 16)
        val high = Spectrum.bars(tone(400), 16)
        assertEquals(low.indices.maxBy { low[it] } < 8, true)
        assertEquals(high.indices.maxBy { high[it] } >= 8, true)
    }
}

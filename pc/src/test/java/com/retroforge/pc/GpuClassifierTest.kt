package com.retroforge.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuClassifierTest {

    private fun gl(renderer: String?) = GpuIdentity(glRenderer = renderer, kgslModel = null, socModel = null)

    private fun level(renderer: String?) = GpuClassifier.classify(gl(renderer)).level

    @Test
    fun parsesAdrenoModelFromGlRenderer() {
        assertEquals(740, GpuClassifier.adrenoModel(gl("Adreno (TM) 740")))
        assertEquals(830, GpuClassifier.adrenoModel(gl("Adreno (TM) 830")))
    }

    @Test
    fun prefersKgslModelOverRenderer() {
        val identity = GpuIdentity(glRenderer = "Adreno (TM) 740", kgslModel = "Adreno750v2", socModel = null)
        assertEquals(750, GpuClassifier.adrenoModel(identity))
    }

    @Test
    fun supportedFamilies() {
        assertEquals(PcSupportLevel.SUPPORTED, level("Adreno (TM) 730"))
        assertEquals(PcSupportLevel.SUPPORTED, level("Adreno (TM) 750"))
        assertEquals(PcSupportLevel.SUPPORTED, level("Adreno (TM) 830"))
    }

    @Test
    fun experimentalFamilies() {
        assertEquals(PcSupportLevel.EXPERIMENTAL, level("Adreno (TM) 850"))
        assertEquals(PcSupportLevel.EXPERIMENTAL, level("Adreno (TM) 725"))
        assertEquals(PcSupportLevel.EXPERIMENTAL, level("Adreno (TM) 720"))
        assertEquals(PcSupportLevel.EXPERIMENTAL, level("Adreno (TM) 650"))
        assertEquals(PcSupportLevel.EXPERIMENTAL, level("Adreno (TM) 618"))
    }

    @Test
    fun unsupportedGpus() {
        assertEquals(PcSupportLevel.UNSUPPORTED, level("Adreno (TM) 710"))
        assertEquals(PcSupportLevel.UNSUPPORTED, level("Adreno (TM) 540"))
        assertEquals(PcSupportLevel.UNSUPPORTED, level("Mali-G715-Immortalis MC11"))
        assertEquals(PcSupportLevel.UNSUPPORTED, level("Samsung Xclipse 940"))
        assertEquals(PcSupportLevel.UNSUPPORTED, level("PowerVR Rogue GE8320"))
        assertEquals(PcSupportLevel.UNSUPPORTED, level("Android Emulator OpenGL ES Translator (Google SwiftShader)"))
    }

    @Test
    fun unknownWhenNothingReadable() {
        val result = GpuClassifier.classify(gl(null))
        assertEquals(PcSupportLevel.UNKNOWN, result.level)
        assertNull(result.adrenoModel)
    }
}

package com.retroforge.pc

import com.retroforge.core.ui.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpuDriversTest {
    // No Android resources in unit tests: messages are looked up as placeholders.
    @Before
    fun placeholderTexts() { AppStrings.testLookup = { id, _, _ -> "#$id" } }

    @Test
    fun keepsOnlyTurnipZips() {
        val releases = """
            [{"tag_name":"v840","name":"Qualcomm Driver v840","published_at":"2026-01-14T00:00:00Z",
              "assets":[{"name":"Qualcomm_840_adpkg.zip","browser_download_url":"https://example.invalid/q.zip","size":10}]},
             {"tag_name":"v26.0.0-rc08","name":"v26.0.0 - Revision 8","published_at":"2026-01-08T00:00:00Z",
              "assets":[{"name":"Turnip_v26.0.0_R8.zip","browser_download_url":"https://example.invalid/t.zip","size":20},
                        {"name":"notes.txt","browser_download_url":"https://example.invalid/n.txt","size":1}]}]
        """.trimIndent()
        val files = GpuDrivers.parse(GpuDrivers.SOURCES.first(), releases)
        assertEquals(listOf("Turnip_v26.0.0_R8.zip"), files.map { it.fileName })
        assertEquals("2026-01-08", files.single().published)
    }

    @Test
    fun adviceByGpu() {
        assertFalse(GpuDrivers.advice(GpuIdentity("Samsung Xclipse 920", null, null)).canUseCustomDrivers)
        assertFalse(GpuDrivers.advice(GpuIdentity("Mali-G715", null, null)).canUseCustomDrivers)
        assertTrue(GpuDrivers.advice(GpuIdentity("Adreno (TM) 740", null, null)).canUseCustomDrivers)
        assertTrue(GpuDrivers.advice(GpuIdentity("Adreno (TM) 830", null, null)).preferA8xx)
        assertFalse(GpuDrivers.advice(GpuIdentity("Adreno (TM) 506", null, null)).canUseCustomDrivers)
    }
}

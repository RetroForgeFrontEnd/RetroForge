package com.retroforge.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverRecommendationsTest {
    private fun rec(family: GpuFamily, target: DriverTarget) = DriverRecommendations.recommend(family, target)

    @Test
    fun familyFromGpuName() {
        assertEquals(GpuFamily.ADRENO_6XX, GpuFamily.of(GpuIdentity("Adreno (TM) 619", null, null)))
        assertEquals(GpuFamily.ADRENO_7XX, GpuFamily.of(GpuIdentity(null, "Adreno740v2", null)))
        assertEquals(GpuFamily.ADRENO_8XX, GpuFamily.of(GpuIdentity("Adreno (TM) 830", null, null)))
        assertEquals(GpuFamily.ADRENO_OLD, GpuFamily.of(GpuIdentity("Adreno (TM) 506", null, null)))
        assertEquals(GpuFamily.XCLIPSE, GpuFamily.of(GpuIdentity("Samsung Xclipse 920", null, null)))
        assertEquals(GpuFamily.MALI, GpuFamily.of(GpuIdentity("Mali-G715", null, null)))
        assertEquals(GpuFamily.MALI, GpuFamily.of(GpuIdentity("Immortalis-G720", null, null)))
        assertEquals(GpuFamily.POWERVR, GpuFamily.of(GpuIdentity("PowerVR Rogue GE8320", null, null)))
        assertEquals(GpuFamily.UNKNOWN, GpuFamily.of(GpuIdentity(null, null, null)))
    }

    @Test
    fun onlyAdrenoSixHundredAndUpGetTurnip() {
        for (family in listOf(GpuFamily.MALI, GpuFamily.XCLIPSE, GpuFamily.POWERVR, GpuFamily.ADRENO_OLD, GpuFamily.UNKNOWN)) {
            for (target in DriverTarget.entries) assertEquals("$family $target", DriverPick.PHONE, rec(family, target).pick)
        }
        assertEquals(DriverReason.NOT_ADRENO, rec(GpuFamily.XCLIPSE, DriverTarget.EDEN).reason)
    }

    @Test
    fun tabA9PlusFindings() {
        // Adreno 619 with Turnip T30: PPSSPP and LRPS2 see no GPU, Dolphin draws.
        assertEquals(DriverRecommendation(DriverPick.PHONE, Confidence.TESTED, DriverReason.TESTED_NO_GPU), rec(GpuFamily.ADRENO_6XX, DriverTarget.BUILT_IN_PPSSPP))
        assertEquals(DriverRecommendation(DriverPick.PHONE, Confidence.TESTED, DriverReason.TESTED_NO_GPU), rec(GpuFamily.ADRENO_6XX, DriverTarget.BUILT_IN_PCSX2))
        assertEquals(DriverRecommendation(DriverPick.TURNIP, Confidence.TESTED, DriverReason.TESTED_RENDERS), rec(GpuFamily.ADRENO_6XX, DriverTarget.BUILT_IN_DOLPHIN))
    }

    @Test
    fun customDriversDontTouchOpenGlOrAppsWithoutAManager() {
        for (family in listOf(GpuFamily.ADRENO_6XX, GpuFamily.ADRENO_7XX, GpuFamily.ADRENO_8XX)) {
            assertEquals(DriverReason.OPENGL_ONLY, rec(family, DriverTarget.BUILT_IN_OTHER).reason)
            assertEquals(DriverReason.APP_CANT_LOAD, rec(family, DriverTarget.NETHERSX2).reason)
            assertEquals(DriverPick.TURNIP, rec(family, DriverTarget.PC_PLAYER).pick)
        }
        assertEquals(DriverTarget.BUILT_IN_OTHER, DriverTarget.builtIn("ppsspp", vulkan = false))
        assertEquals(DriverTarget.BUILT_IN_DOLPHIN, DriverTarget.builtIn("dolphin", vulkan = true))
        assertEquals(DriverTarget.BUILT_IN_OTHER, DriverTarget.builtIn("flycast", vulkan = true))
        assertEquals(DriverTarget.EDEN, DriverTarget.standalone("EDEN"))
        assertNull(DriverTarget.standalone("REDREAM"))
    }

    @Test
    fun eightHundredsStayOnThePhoneDriverExceptPc() {
        for (target in listOf(DriverTarget.EDEN, DriverTarget.DOLPHIN_APP, DriverTarget.AZAHAR, DriverTarget.BUILT_IN_DOLPHIN)) {
            assertEquals(DriverPick.PHONE, rec(GpuFamily.ADRENO_8XX, target).pick)
            assertEquals(DriverPick.TURNIP, rec(GpuFamily.ADRENO_7XX, target).pick)
        }
        assertEquals(Confidence.GUESS, rec(GpuFamily.ADRENO_8XX, DriverTarget.PC_PLAYER).confidence)
    }

    @Test
    fun playerChoice() {
        val dolphin = DriverTarget.BUILT_IN_DOLPHIN
        val ppsspp = DriverTarget.BUILT_IN_PPSSPP
        assertFalse(DriverRecommendations.useCustomDriver(null, GpuFamily.ADRENO_6XX, dolphin, installed = false))
        assertTrue(DriverRecommendations.useCustomDriver(null, GpuFamily.ADRENO_6XX, dolphin, installed = true))
        assertFalse(DriverRecommendations.useCustomDriver(null, GpuFamily.ADRENO_6XX, ppsspp, installed = true))
        assertTrue(DriverRecommendations.useCustomDriver("turnip", GpuFamily.ADRENO_6XX, ppsspp, installed = true))
        assertFalse(DriverRecommendations.useCustomDriver("phone", GpuFamily.ADRENO_6XX, dolphin, installed = true))
        // GPU not looked at yet: the installed driver, as before recommendations.
        assertTrue(DriverRecommendations.useCustomDriver(null, null, ppsspp, installed = true))
    }

    @Test
    fun suggestedBuildMatchesTheFamily() {
        fun file(name: String, date: String) = DriverFile("s", name, "$name.zip", "u", 1, date)
        val files = listOf(file("Turnip_v26_a8xx", "2026-10-01"), file("Turnip_v26_R2", "2026-09-01"), file("Turnip_v25", "2026-05-01"))
        assertEquals("Turnip_v26_R2", DriverRecommendations.suggestedBuild(GpuFamily.ADRENO_7XX, files)?.release)
        assertEquals("Turnip_v26_a8xx", DriverRecommendations.suggestedBuild(GpuFamily.ADRENO_8XX, files)?.release)
        assertNull(DriverRecommendations.suggestedBuild(GpuFamily.MALI, files))
        // Galaxy S22 (SM-S901B): Exynos 2200, Xclipse 920. No Turnip download is ever suggested there.
        assertNull(DriverRecommendations.suggestedBuild(GpuFamily.XCLIPSE, files))
        assertFalse(GpuFamily.XCLIPSE.canUseTurnip)
    }
}

package com.retroforge.pc

/** Kinds of GPU, as far as driver choice goes: only Adreno 6xx and newer can load Turnip. */
enum class GpuFamily {
    ADRENO_6XX, ADRENO_7XX, ADRENO_8XX, ADRENO_OLD, MALI, XCLIPSE, POWERVR, UNKNOWN;

    val canUseTurnip get() = this == ADRENO_6XX || this == ADRENO_7XX || this == ADRENO_8XX

    companion object {
        fun of(identity: GpuIdentity): GpuFamily {
            GpuClassifier.adrenoModel(identity)?.let { model ->
                return when (model) {
                    in 800..899 -> ADRENO_8XX
                    in 700..799 -> ADRENO_7XX
                    in 600..699 -> ADRENO_6XX
                    else -> ADRENO_OLD
                }
            }
            val text = listOfNotNull(identity.glRenderer, identity.kgslModel, identity.socModel).joinToString(" ")
            return when {
                text.contains("xclipse", true) -> XCLIPSE
                text.contains("mali", true) || text.contains("immortalis", true) -> MALI
                text.contains("powervr", true) -> POWERVR
                else -> UNKNOWN
            }
        }
    }
}

/** What draws a system's games, as far as its GPU driver goes. */
enum class DriverTarget {
    /** The built-in player's Vulkan cores. */
    BUILT_IN_PPSSPP, BUILT_IN_PCSX2, BUILT_IN_DOLPHIN,
    /** Built-in cores that draw with OpenGL ES or in software: custom drivers don't touch them. */
    BUILT_IN_OTHER,
    /** Standalone apps, each with its own driver setting (or none). */
    EDEN, DOLPHIN_APP, NETHERSX2, ARMSX2, AZAHAR, PPSSPP_APP,
    /** The PC player (Wine, DXVK). */
    PC_PLAYER;

    /** Whether RetroForge itself picks the driver (its own players), rather than another app. */
    val inRetroForge get() = this == BUILT_IN_PPSSPP || this == BUILT_IN_PCSX2 || this == BUILT_IN_DOLPHIN || this == PC_PLAYER

    companion object {
        /** The built-in player's target for [coreId] drawing with Vulkan ([vulkan]) or not. */
        fun builtIn(coreId: String, vulkan: Boolean): DriverTarget = when {
            !vulkan -> BUILT_IN_OTHER
            coreId == "ppsspp" -> BUILT_IN_PPSSPP
            coreId == "pcsx2" -> BUILT_IN_PCSX2
            coreId == "dolphin" -> BUILT_IN_DOLPHIN
            else -> BUILT_IN_OTHER
        }

        /** The target for a standalone emulator id from standalone.json, or null when its driver doesn't matter here. */
        fun standalone(emulatorId: String): DriverTarget? = when (emulatorId.uppercase()) {
            "EDEN" -> EDEN
            "DOLPHIN" -> DOLPHIN_APP
            "NETHERSX2" -> NETHERSX2
            "ARMSX2" -> ARMSX2
            "AZAHAR" -> AZAHAR
            "PPSSPP" -> PPSSPP_APP
            else -> null
        }
    }
}

enum class DriverPick { PHONE, TURNIP }

/** How sure the recommendation is. */
enum class Confidence {
    /** Tried on a RetroForge test device. */
    TESTED,
    /** From the emulator's own documentation or well-known driver builders' notes. */
    COMMUNITY,
    /** Nobody has said; a reasoned guess. */
    GUESS,
}

/** Why, in a word the app turns into a sentence. */
enum class DriverReason {
    NOT_ADRENO, OLD_ADRENO, UNKNOWN_GPU, OPENGL_ONLY, APP_CANT_LOAD,
    TESTED_RENDERS, TURNIP_MORE_COMPLETE, TURNIP_LIKELY, SMALL_GAIN, A8XX_NEW, PC_NEEDS_TURNIP,
}

data class DriverRecommendation(val pick: DriverPick, val confidence: Confidence, val reason: DriverReason)

/**
 * The GPU driver to use for each emulator on each kind of GPU. Sources (October 2026):
 * - RetroForge's own tests on a Galaxy Tab A9+ (Adreno 619, Turnip T30): PPSSPP, LRPS2 and Dolphin all draw on Turnip.
 * - Dolphin's progress report (May–July 2023): Turnip is "by far the most complete Vulkan driver" for Adreno.
 * - PPSSPP's custom-driver page: PPSSPP uses few GPU features, so gains are small ("don't expect miracles").
 * - Eden's driver manager fetches and suggests a Turnip build; Qualcomm never shipped Vulkan 1.3 for Adreno 6xx.
 * - NetherSX2 can't select a custom driver (hence the separate NetherSX2-Turnip fork).
 * - Winlator's notes: Turnip on Adreno 6xx/7xx for DXVK; a8xx Turnip builds are new (sysmem-only, Eden glitches).
 * Where none of them says, the entry is a [Confidence.GUESS]. The phone's driver wins any tie: it never needs a download.
 */
object DriverRecommendations {

    fun recommend(family: GpuFamily, target: DriverTarget): DriverRecommendation {
        val phone = DriverPick.PHONE
        val turnip = DriverPick.TURNIP
        when (family) {
            GpuFamily.MALI, GpuFamily.XCLIPSE, GpuFamily.POWERVR -> return DriverRecommendation(phone, Confidence.COMMUNITY, DriverReason.NOT_ADRENO)
            GpuFamily.ADRENO_OLD -> return DriverRecommendation(phone, Confidence.COMMUNITY, DriverReason.OLD_ADRENO)
            GpuFamily.UNKNOWN -> return DriverRecommendation(phone, Confidence.GUESS, DriverReason.UNKNOWN_GPU)
            else -> Unit
        }
        if (target == DriverTarget.BUILT_IN_OTHER) return DriverRecommendation(phone, Confidence.COMMUNITY, DriverReason.OPENGL_ONLY)
        if (target == DriverTarget.NETHERSX2) return DriverRecommendation(phone, Confidence.COMMUNITY, DriverReason.APP_CANT_LOAD)
        val a6 = family == GpuFamily.ADRENO_6XX
        val a8 = family == GpuFamily.ADRENO_8XX
        return when (target) {
            DriverTarget.BUILT_IN_PPSSPP, DriverTarget.PPSSPP_APP -> DriverRecommendation(phone, Confidence.COMMUNITY, DriverReason.SMALL_GAIN)
            DriverTarget.BUILT_IN_PCSX2 -> when {
                a6 -> DriverRecommendation(turnip, Confidence.TESTED, DriverReason.TESTED_RENDERS)
                a8 -> DriverRecommendation(phone, Confidence.GUESS, DriverReason.A8XX_NEW)
                else -> DriverRecommendation(turnip, Confidence.GUESS, DriverReason.TURNIP_LIKELY)
            }
            DriverTarget.ARMSX2 ->
                if (a8) DriverRecommendation(phone, Confidence.GUESS, DriverReason.A8XX_NEW)
                else DriverRecommendation(turnip, Confidence.GUESS, DriverReason.TURNIP_LIKELY)
            DriverTarget.BUILT_IN_DOLPHIN -> when {
                a6 -> DriverRecommendation(turnip, Confidence.TESTED, DriverReason.TESTED_RENDERS)
                a8 -> DriverRecommendation(phone, Confidence.GUESS, DriverReason.A8XX_NEW)
                else -> DriverRecommendation(turnip, Confidence.COMMUNITY, DriverReason.TURNIP_MORE_COMPLETE)
            }
            DriverTarget.DOLPHIN_APP, DriverTarget.EDEN, DriverTarget.AZAHAR ->
                if (a8) DriverRecommendation(phone, Confidence.GUESS, DriverReason.A8XX_NEW)
                else DriverRecommendation(turnip, Confidence.COMMUNITY, DriverReason.TURNIP_MORE_COMPLETE)
            DriverTarget.PC_PLAYER ->
                DriverRecommendation(turnip, if (a8) Confidence.GUESS else Confidence.COMMUNITY, DriverReason.PC_NEEDS_TURNIP)
            DriverTarget.BUILT_IN_OTHER, DriverTarget.NETHERSX2 -> error("handled above")
        }
    }

    /**
     * Whether RetroForge's own player should load the custom driver for one system: the user's [choice] ("phone",
     * "turnip"), else the recommendation; never when there's no driver installed or the GPU can't use one.
     */
    fun useCustomDriver(choice: String?, family: GpuFamily?, target: DriverTarget, installed: Boolean): Boolean {
        if (!installed) return false
        return when (choice) {
            CHOICE_PHONE -> false
            CHOICE_TURNIP -> true
            // GPU not known yet (Settings hasn't looked): keep to the driver the user installed, as before.
            else -> family == null || recommend(family, target).pick == DriverPick.TURNIP
        }
    }

    /** The newest Turnip build to suggest for [family]: a8xx builds on Adreno 8xx, the others elsewhere. */
    fun suggestedBuild(family: GpuFamily, files: List<DriverFile>): DriverFile? {
        if (!family.canUseTurnip) return null
        val a8xx = { f: DriverFile -> f.fileName.contains("8xx", true) || f.release.contains("8xx", true) }
        val newest = files.sortedByDescending { it.published }
        return if (family == GpuFamily.ADRENO_8XX) newest.firstOrNull(a8xx) else newest.firstOrNull { !a8xx(it) }
    }

    const val CHOICE_PHONE = "phone"
    const val CHOICE_TURNIP = "turnip"

    /** The key in the player's video.properties holding a system's driver choice. */
    fun choiceKey(system: String) = "driver.$system"

    /** The PC player's "system" for [choiceKey]. */
    const val PC_SYSTEM = "windows"
}

package com.retroforge.app.ui.pc

/**
 * How well a PC game runs in RetroForge's PC player: RetroForge's own test results (games tested on real devices,
 * listed here with each release) and how it's gone on this device (from the player's history). Nothing comes from
 * other apps or services.
 */
object PcCompatibility {

    enum class Rating(val label: String) {
        PLAYS_WELL("✓ Plays well"),
        ISSUES("⚠ Runs with issues"),
        DOESNT_RUN("✕ Doesn't run yet"),
    }

    data class Tested(val rating: Rating, val note: String)

    /** RetroForge's tested games: by Steam app id where the game has one, else by its name (letters and digits). */
    private val tested: Map<String, Tested> = mapOf(
        "2368300" to Tested(Rating.PLAYS_WELL, "Godot (Vulkan), .NET. Tested on a Galaxy S22, with Steam Cloud saves."),
        "12150" to Tested(Rating.PLAYS_WELL, "Direct3D 8/9 through DXVK. Tested on a Galaxy S22."),
        "maxpayne2" to Tested(Rating.PLAYS_WELL, "Direct3D 8/9 through DXVK. Tested on a Galaxy S22."),
        "391540" to Tested(Rating.PLAYS_WELL, "GameMaker, Direct3D 9. Tested on a Galaxy S22."),
        "undertale" to Tested(Rating.PLAYS_WELL, "GameMaker, Direct3D 9. Tested on a Galaxy S22."),
        "ctrnative" to Tested(Rating.ISSUES, "OpenGL through zink: plays, but some textures (text, eyes) are missing on Samsung Xclipse GPUs."),
    )

    private fun simple(s: String) = s.lowercase().replace(Regex("""\((retroforge|gog|steam)\)"""), "").filter { it.isLetterOrDigit() }

    /** RetroForge's own result for this game, if it's been tested. */
    fun tested(title: String, steamAppId: String?): Tested? = steamAppId?.let { tested[it] } ?: tested[simple(title)]

    /**
     * The short label for a game's tile: what happened on this device last time if it didn't start, else
     * RetroForge's tested result, else whether it's been played here. Null when there's nothing to say.
     */
    fun label(title: String, steamAppId: String?, history: com.retroforge.pcplayer.PcHistory.Entry?): String? = when {
        history?.lastFailed == true -> "⚠ Didn't start last time"
        tested(title, steamAppId) != null -> tested(title, steamAppId)!!.rating.label
        history != null && history.minutesPlayed > 0 -> "▶ Played ${playtime(history.minutesPlayed)}"
        else -> null
    }

    private fun playtime(minutes: Int) = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

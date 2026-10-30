package com.retroforge.app.ui.pc

import com.retroforge.app.R
import com.retroforge.core.ui.str

/**
 * How well a PC game runs in RetroForge's PC player: RetroForge's own test results (games tested on real devices,
 * listed here with each release) and how it's gone on this device (from the player's history). Nothing comes from
 * other apps or services.
 */
object PcCompatibility {

    enum class Rating(private val labelId: Int) {
        PLAYS_WELL(R.string.pc_compatibility_plays_well),
        ISSUES(R.string.pc_compatibility_runs_with_issues),
        DOESNT_RUN(R.string.pc_compatibility_doesn_t_run_yet);

        val label: String get() = str(labelId)
    }

    data class Tested(val rating: Rating, val note: String)

    /** RetroForge's tested games: by Steam app id where the game has one, else by its name (letters and digits). */
    private val tested: Map<String, Tested> get() = mapOf(
        "2368300" to Tested(Rating.PLAYS_WELL, str(R.string.pc_compatibility_godot_vulkan_net_tested_on_a)),
        "12150" to Tested(Rating.PLAYS_WELL, str(R.string.pc_compatibility_direct3d_8_9_through_dxvk_tested)),
        "maxpayne2" to Tested(Rating.PLAYS_WELL, str(R.string.pc_compatibility_direct3d_8_9_through_dxvk_tested)),
        "391540" to Tested(Rating.PLAYS_WELL, str(R.string.pc_compatibility_gamemaker_direct3d_9_tested_on_a)),
        "undertale" to Tested(Rating.PLAYS_WELL, str(R.string.pc_compatibility_gamemaker_direct3d_9_tested_on_a)),
        "ctrnative" to Tested(Rating.ISSUES, str(R.string.pc_compatibility_opengl_through_zink_plays_but_some)),
    )

    private fun simple(s: String) = s.lowercase().replace(Regex("""\((retroforge|gog|steam)\)"""), "").filter { it.isLetterOrDigit() }

    /** RetroForge's own result for this game, if it's been tested. */
    fun tested(title: String, steamAppId: String?): Tested? = steamAppId?.let { tested[it] } ?: tested[simple(title)]

    /**
     * The short label for a game's tile: what happened on this device last time if it didn't start, else
     * RetroForge's tested result, else whether it's been played here. Null when there's nothing to say.
     */
    fun label(title: String, steamAppId: String?, history: com.retroforge.pcplayer.PcHistory.Entry?): String? = when {
        history?.lastFailed == true -> str(R.string.pc_compatibility_didn_t_start_last_time)
        tested(title, steamAppId) != null -> tested(title, steamAppId)!!.rating.label
        history != null && history.minutesPlayed > 0 -> str(R.string.pc_compatibility_played, playtime(history.minutesPlayed))
        else -> null
    }

    private fun playtime(minutes: Int) = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

package com.retroforge.app.ui.media

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroforge.app.R
import com.retroforge.app.player.MenuButton
import com.retroforge.app.player.MenuCyan
import com.retroforge.app.player.MenuGrey
import com.retroforge.app.player.MenuOrange

/**
 * One EQ for RetroForge's music and films: Android's own equaliser on each player's sound, with the same settings for
 * both, kept between songs and films. Settings: off, one of the phone's presets, or the user's own band levels.
 */
object SoundEq {
    private const val PREFS = "sound_eq"
    private val live = mutableListOf<Equalizer>()
    private var prefs: android.content.SharedPreferences? = null

    var enabled = false; private set
    /** The preset in use, or -1 for the user's own levels. */
    var preset = -1; private set
    private var levels = ShortArray(0)

    /** Puts the EQ on a player's sound ([sessionId] from ExoPlayer); [detach] it when the player goes. */
    @Synchronized
    fun attach(context: Context, sessionId: Int): Equalizer? {
        load(context)
        val eq = runCatching { Equalizer(0, sessionId) }.getOrNull() ?: return null
        live += eq
        apply(eq)
        return eq
    }

    @Synchronized
    fun detach(eq: Equalizer?) {
        eq ?: return
        live.remove(eq)
        runCatching { eq.release() }
    }

    private fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
        preset = p.getInt("preset", -1)
        levels = p.getString("levels", null)?.split(',')?.mapNotNull { it.toShortOrNull() }?.toShortArray() ?: ShortArray(0)
    }

    private fun save() {
        prefs?.edit()?.putBoolean("enabled", enabled)?.putInt("preset", preset)?.putString("levels", levels.joinToString(","))?.apply()
    }

    private fun apply(eq: Equalizer) = runCatching {
        eq.enabled = enabled
        if (!enabled) return@runCatching
        if (preset >= 0 && preset < eq.numberOfPresets) eq.usePreset(preset.toShort())
        else levels.forEachIndexed { band, level -> if (band < eq.numberOfBands) eq.setBandLevel(band.toShort(), level) }
    }

    /** What the phone's equaliser offers: its presets, its bands' centres (Hz) and the level range (millibels). */
    class Info(val presets: List<String>, val centres: List<Int>, val min: Short, val max: Short)

    @Synchronized
    fun info(): Info? {
        val eq = live.firstOrNull() ?: return null
        return runCatching {
            Info((0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }, (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                eq.bandLevelRange[0], eq.bandLevelRange[1])
        }.getOrNull()
    }

    /** Each band's level now (millibels). */
    @Synchronized
    fun currentLevels(): ShortArray = live.firstOrNull()?.let { eq -> runCatching { ShortArray(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()) } }.getOrNull() } ?: levels

    @Synchronized
    fun setEnabled(on: Boolean) { enabled = on; live.forEach(::apply); save() }

    @Synchronized
    fun usePreset(index: Int) { enabled = true; preset = index; live.forEach(::apply); levels = currentLevels(); save() }

    @Synchronized
    fun setBand(band: Int, level: Short) {
        enabled = true
        val now = currentLevels().copyOf()
        if (band >= now.size) return
        now[band] = level
        levels = now
        preset = -1
        live.forEach(::apply)
        save()
    }
}

/** The EQ's controls, in the player's look: on/off, the phone's presets, and a slider for each band. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqPanel() {
    val info = remember { SoundEq.info() }
    var changed by remember { mutableIntStateOf(0) }
    if (info == null) {
        Text(stringResource(R.string.eq_start_playing), color = MenuGrey, fontSize = 13.sp)
        return
    }
    @Suppress("UNUSED_EXPRESSION") changed // read so the panel redraws on every change
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MenuButton(onClick = { SoundEq.setEnabled(!SoundEq.enabled); changed++ }, primary = SoundEq.enabled) {
                Text(stringResource(if (SoundEq.enabled) R.string.eq_on else R.string.eq_off))
            }
            info.presets.forEachIndexed { i, name ->
                MenuButton(onClick = { SoundEq.usePreset(i); changed++ }) {
                    Text((if (SoundEq.enabled && SoundEq.preset == i) "● " else "") + name, color = if (SoundEq.enabled && SoundEq.preset == i) MenuOrange else MenuCyan)
                }
            }
        }
        val levels = SoundEq.currentLevels()
        info.centres.forEachIndexed { band, hz ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", color = MenuGrey, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                Slider(
                    value = (levels.getOrNull(band) ?: 0).toFloat(),
                    onValueChange = { SoundEq.setBand(band, it.toInt().toShort()); changed++ },
                    valueRange = info.min.toFloat()..info.max.toFloat(),
                    enabled = SoundEq.enabled,
                    colors = SliderDefaults.colors(thumbColor = MenuOrange, activeTrackColor = MenuOrange, inactiveTrackColor = MenuGrey.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f),
                )
                Text("%+d dB".format((levels.getOrNull(band) ?: 0) / 100), color = MenuCyan, fontSize = 12.sp, modifier = Modifier.width(52.dp))
            }
        }
    }
}

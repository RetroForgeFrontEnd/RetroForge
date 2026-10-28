package com.retroforge.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.retro.HotkeyCombo

/** Every hotkey in one place: RetroForge's own controls, the RetroArch combos it sets, and RetroArch's defaults. */
@Composable
fun HotkeysScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val menu = HotkeyCombo.of(settings.menuCombo).label
    val quit = HotkeyCombo.of(settings.quitCombo).label

    ScreenFrame(title = "Hotkeys", subtitle = "Change the RetroArch combos under Settings → RetroArch hotkeys.") {
        Section("In RetroForge", listOf(
            "D-pad or stick" to "Move",
            "A" to "Select",
            "B" to "Back",
            "L1 / R1" to "Previous / next section",
        ))
        if (settings.retroArchHotkeys) {
            Section("In games (RetroArch, set by RetroForge)", listOf(
                quit to "Quit the game and return to RetroForge",
                menu to "Open RetroArch's menu",
            ) + if (settings.fastForwardOnR2) listOf("Hold R2" to "Fast-forward (GBA, SNES, Mega Drive and other systems without R2)") else emptyList())
        } else {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("In games (RetroArch)")
                CardBody("RetroForge's hotkeys are off, so RetroArch uses its own settings.")
            }
        }
        Section("In RetroArch's menu (Quick Menu)", listOf(
            "Save States" to "Save or load a state",
            "Disc Control" to "Eject, choose the next disc, insert",
            "Restart" to "Reset the game",
            "Shaders" to "CRT and other screen effects",
            "Close Content" to "Leave the game without quitting RetroArch",
        ))
        Section("RetroArch keyboard defaults", listOf(
            "F1" to "Menu",
            "Esc" to "Quit",
            "F2 / F4" to "Save / load state",
            "F7" to "Next state slot",
            "H" to "Reset",
            "Space / L (hold)" to "Fast-forward (toggle / hold)",
            "E (hold)" to "Slow motion",
            "R (hold)" to "Rewind (when Rewind Support is on)",
            "P" to "Pause",
            "K" to "Frame advance",
            "F9" to "Mute",
        ))
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Standalone emulators")
            CardBody(
                "DuckStation, Dolphin, PPSSPP and the other standalone apps have their own menus and hotkeys, set in each app's settings. " +
                    "RetroForge can't change them, because Android doesn't let one app read the controller while another is on screen.",
            )
        }
    }
}

@Composable
private fun Section(title: String, rows: List<Pair<String, String>>) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(title)
        rows.forEach { (keys, action) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(keys, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.4f))
                Text(action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

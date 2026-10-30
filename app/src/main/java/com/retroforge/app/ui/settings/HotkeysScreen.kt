package com.retroforge.app.ui.settings

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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

    ScreenFrame(title = stringResource(R.string.hotkeys_hotkeys), subtitle = stringResource(R.string.hotkeys_change_the_retroarch_combos_under_settings)) {
        Section(str(R.string.hotkeys_in_retroforge), listOf(
            str(R.string.hotkeys_d_pad_or_stick) to str(R.string.hotkeys_move),
            "A" to str(R.string.hotkeys_select),
            "B" to str(R.string.retro_forge_root_back),
            "L1 / R1" to str(R.string.hotkeys_previous_next_section),
        ))
        if (settings.retroArchHotkeys) {
            Section(str(R.string.hotkeys_in_games_retroarch_set_by_retroforge), listOf(
                quit to str(R.string.hotkeys_quit_the_game_and_return_to),
                menu to str(R.string.hotkeys_open_retroarch_s_menu),
            ) + if (settings.fastForwardOnR2) listOf(str(R.string.hotkeys_hold_r2) to str(R.string.hotkeys_fast_forward_gba_snes_mega_drive)) else emptyList())
        } else {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.hotkeys_in_games_retroarch))
                CardBody(stringResource(R.string.hotkeys_retroforge_s_hotkeys_are_off_so))
            }
        }
        Section(str(R.string.hotkeys_in_retroarch_s_menu_quick_menu), listOf(
            "Save States" to str(R.string.hotkeys_save_or_load_a_state),
            "Disc Control" to str(R.string.hotkeys_eject_choose_the_next_disc_insert),
            "Restart" to str(R.string.hotkeys_reset_the_game),
            "Shaders" to str(R.string.hotkeys_crt_and_other_screen_effects),
            "Close Content" to str(R.string.hotkeys_leave_the_game_without_quitting_retroarch),
        ))
        Section(str(R.string.hotkeys_retroarch_keyboard_defaults), listOf(
            "F1" to str(R.string.hotkeys_menu),
            "Esc" to str(R.string.hotkeys_quit),
            "F2 / F4" to str(R.string.hotkeys_save_load_state),
            "F7" to str(R.string.hotkeys_next_state_slot),
            "H" to str(R.string.hotkeys_reset),
            str(R.string.hotkeys_space_l_hold) to str(R.string.hotkeys_fast_forward_toggle_hold),
            str(R.string.hotkeys_e_hold) to str(R.string.hotkeys_slow_motion),
            str(R.string.hotkeys_r_hold) to str(R.string.hotkeys_rewind_when_rewind_support_is_on),
            "P" to str(R.string.hotkeys_pause),
            "K" to str(R.string.hotkeys_frame_advance),
            "F9" to str(R.string.hotkeys_mute),
        ))
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.hotkeys_standalone_emulators))
            CardBody(
                stringResource(R.string.hotkeys_duckstation_dolphin_ppsspp_and_the_other),
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

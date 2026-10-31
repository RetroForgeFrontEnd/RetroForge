package com.retroforge.app.ui.settings

import com.retroforge.core.ui.pluralStr
import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import com.retroforge.app.ui.ChipRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import com.retroforge.core.ui.ControllerTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.net.toUri
import com.retroforge.artwork.SteamGridDb
import com.retroforge.artwork.ScreenScraper
import com.retroforge.app.BuildConfig
import com.retroforge.retro.StandaloneLauncher
import com.retroforge.retro.HotkeyCombo
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.retroforge.core.ui.FocusChip
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.legalNoticeParagraphs
import com.retroforge.core.settings.ThemeMode
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenSetup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHotkeys: () -> Unit,
    onOpenControllerTest: () -> Unit = {},
    onOpenGpu: () -> Unit,
    onOpenTexturePacks: () -> Unit = {},
    onOpenCheck: () -> Unit = {},
    library: com.retroforge.core.library.LibraryRepository? = null,
    onOpenApps: () -> Unit = {},
    onOpenTutorial: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenPrivacy: () -> Unit,
    playerSystems: List<Pair<String, String>> = emptyList(),
    coreChoices: com.retroforge.player.CoreChoices? = null,
    viewPrefs: android.content.SharedPreferences? = null,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Settings in a few groups, each on its own page, so nothing is far down a long list. Back returns to the groups.
    var group by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    com.retroforge.app.ui.CloseStep(enabled = group != null) { group = null }
    val groups = listOf(
        "games" to (str(R.string.settings_games_and_library) to str(R.string.settings_game_folders_and_network_drives_es)),
        "playing" to (str(R.string.settings_playing) to str(R.string.settings_the_built_in_player_saves_backups)),
        "covers" to (str(R.string.settings_covers_and_bezels) to str(R.string.settings_steamgriddb_and_screenscraper_accounts_for_box)),
        "looks" to (str(R.string.settings_looks_and_sounds) to str(R.string.settings_themes_interface_sounds_android_apps_and)),
        "help" to (str(R.string.settings_help_and_about) to str(R.string.settings_how_to_use_retroforge_updates_set)),
        "kiosk" to (str(R.string.kiosk_title) to str(R.string.kiosk_settings_body)),
    )

    ScreenFrame(title = groups.firstOrNull { it.first == group }?.second?.first ?: str(R.string.settings_settings), compact = true) {
        if (group == null) {
            groups.forEach { (id, text) ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { group = id }) {
                    CardTitle(text.first)
                    CardBody(text.second)
                }
            }
            return@ScreenFrame
        }
        FocusChip(stringResource(R.string.settings_all_settings), onClick = { group = null })
        when (group) {
            "kiosk" -> com.retroforge.app.ui.KioskCard()
            "games" -> {
                com.retroforge.app.ui.NetworkDrivesCard(str(R.string.settings_games), onUse = library?.let { lib ->
                    { uri, progress ->
                        // A short running report while it scans: which system, and how far.
                        val root = lib.addFolderAndWait(uri) { p ->
                            val system = p.currentSystem?.let { " ${lib.systemName(it)}" }.orEmpty()
                            val of = if (p.systemsTotal > 0) str(R.string.settings_of_systems, p.systemsDone * 100 / p.systemsTotal, p.systemsDone, p.systemsTotal) else ""
                            progress(str(R.string.settings_looking_in_files_checked, system, p.filesSeen, of), if (p.systemsTotal > 0) p.systemsDone.toFloat() / p.systemsTotal else null)
                        }
                        when {
                            root == null -> str(R.string.settings_added_it_s_still_scanning_see)
                            root.gameCount == 0 -> str(R.string.settings_added_but_no_games_were_found, root.name)
                            else -> pluralStr(R.plurals.settings_added_games_found, root.gameCount, root.name, root.gameCount)
                        }
                    }
                })

                library?.let { EsdeImportCard(it) }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTexturePacks) {
                    CardTitle(stringResource(R.string.settings_texture_packs))
                    CardBody(stringResource(R.string.settings_hd_textures_that_make_n64_and))
                }

                viewPrefs?.let { prefs ->
                    var scanOnStart by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.ui.library.LibraryViewModel.SCAN_ON_START, false)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        scanOnStart = !scanOnStart
                        prefs.edit().putBoolean(com.retroforge.app.ui.library.LibraryViewModel.SCAN_ON_START, scanOnStart).apply()
                    }) {
                        CardTitle(stringResource(R.string.settings_look_for_new_games_when_retroforge)) { StatusPill(if (scanOnStart) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (scanOnStart) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody(stringResource(R.string.settings_each_time_retroforge_opens_it_checks))
                    }
                    var showAll by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.ui.library.LibraryViewModel.SHOW_ALL_SYSTEMS, false)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        showAll = !showAll
                        prefs.edit().putBoolean(com.retroforge.app.ui.library.LibraryViewModel.SHOW_ALL_SYSTEMS, showAll).apply()
                    }) {
                        CardTitle(stringResource(R.string.settings_show_systems_you_have_no_games)) { StatusPill(if (showAll) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (showAll) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody(stringResource(R.string.settings_on_the_retro_tab_lists_every))
                    }
                }
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.setShowPcSection(!settings.showPcSection) },
                ) {
                    CardTitle(stringResource(R.string.settings_show_the_pc_tab)) { StatusPill(if (settings.showPcSection) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (settings.showPcSection) StatusTone.GOOD else StatusTone.NEUTRAL) }
                    CardBody(stringResource(R.string.settings_turn_off_to_hide_the_pc))
                }

            }
            "playing" -> {
                coreChoices?.let { BuiltInPlayerCard(playerSystems, it, settings.resumeGames, viewModel::setResumeGames, library) }

                SaveSyncCard()

                EmulatorAppsCard()

                viewPrefs?.let { DualScreenCard(it) }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenGpu) {
                    CardTitle(stringResource(R.string.settings_graphics_chip_and_drivers))
                    CardBody(stringResource(R.string.settings_shows_which_graphics_chip_this_device))
                }

                if (SwitchPlayer.installed(LocalContext.current)) SwitchSettingsCard(stringResource(R.string.switch_settings_title), setup = false)

                RetroAchievementsCard()

                NetplayCard()

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenControllerTest) {
                    CardTitle(stringResource(R.string.controller_test_title))
                    CardBody(stringResource(R.string.controller_test_card))
                }

                HotkeysCard(settings.retroArchHotkeys, HotkeyCombo.of(settings.menuCombo), HotkeyCombo.of(settings.quitCombo), viewModel::setRetroArchHotkeys, onOpenHotkeys)
                if (settings.retroArchHotkeys) {
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.setFastForwardOnR2(!settings.fastForwardOnR2) }) {
                        CardTitle(stringResource(R.string.settings_hold_r2_to_fast_forward)) { StatusPill(if (settings.fastForwardOnR2) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (settings.fastForwardOnR2) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody(stringResource(R.string.settings_while_you_hold_r2_the_game))
                    }
                }

            }
            "covers" -> {
                SteamGridDbKeyCard(settings.steamGridDbKey, viewModel::setSteamGridDbKey)

                ScreenScraperCard(settings.screenScraperUser, viewModel::setScreenScraperLogin)

                BezelsCard(playerSystems)

            }
            "looks" -> {
                LanguageCard()

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenThemes) {
                    CardTitle(stringResource(R.string.settings_themes))
                    CardBody(stringResource(R.string.settings_change_retroforge_s_colours_fonts_and, settings.themeMode.name.lowercase()))
                }

                viewPrefs?.let { prefs ->
                    var sounds by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.UI_SOUNDS, true)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        sounds = !sounds
                        prefs.edit().putBoolean(com.retroforge.app.UI_SOUNDS, sounds).apply()
                        com.retroforge.core.ui.UiSounds.enabled = sounds
                    }) {
                        CardTitle(stringResource(R.string.settings_interface_sounds)) { StatusPill(if (sounds) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (sounds) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody(stringResource(R.string.settings_small_sounds_as_you_move_around))
                    }
                }
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenApps) {
                    CardTitle(stringResource(R.string.settings_android_apps_and_home_screen))
                    CardBody(stringResource(R.string.settings_open_your_other_apps_and_android))
                }

            }
            else -> {
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTutorial) {
                    CardTitle(stringResource(R.string.settings_how_to_use_retroforge))
                    CardBody(stringResource(R.string.settings_a_short_guide_to_every_feature))
                    CardBody(stringResource(R.string.settings_with_a_controller_d_pad_to))
                }

                UpdateCard()

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAbout) {
                    CardTitle(stringResource(R.string.settings_about_retroforge))
                    CardBody(stringResource(R.string.settings_version_made_by_darren_glancy, BuildConfig.VERSION_NAME))
                    CardBody(stringResource(R.string.settings_who_made_it_what_it_does))
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenCheck) {
                    CardTitle(stringResource(R.string.settings_check_my_setup))
                    CardBody(stringResource(R.string.settings_checks_every_system_you_have_games))
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSetup) {
                    CardTitle(stringResource(R.string.settings_set_up_retroforge))
                    CardBody(stringResource(R.string.settings_step_by_step_pick_your_games))
                }

                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle(stringResource(R.string.settings_games_bios_and_the_law))
                    legalNoticeParagraphs.forEach { CardBody(it) }
                }

                SupportCard()
                ReportProblemCard()
                ChipRow {
                    FocusChip(stringResource(R.string.settings_thanks_and_licences), onClick = onOpenLicences)
                    FocusChip(stringResource(R.string.settings_privacy), onClick = onOpenPrivacy)
                }

            }
        }
    }
}

@Composable
private fun SteamGridDbKeyCard(saved: String?, onSave: (String?) -> Unit) {
    val context = LocalContext.current
    var draft by remember(saved) { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_box_art_from_steamgriddb))
        if (saved.isNullOrBlank()) {
            StatusPill(stringResource(R.string.settings_no_api_key), StatusTone.NEUTRAL)
            CardBody(
                stringResource(R.string.settings_thousands_of_fan_made_covers_to),
            )
        } else {
            StatusPill(stringResource(R.string.settings_api_key_saved, saved.takeLast(4)), StatusTone.GOOD)
            CardBody(stringResource(R.string.settings_use_change_cover_on_any_game))
        }
        ControllerTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            singleLine = true,
            label = { Text(if (saved.isNullOrBlank()) stringResource(R.string.settings_paste_api_key) else stringResource(R.string.settings_replace_api_key)) },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        ChipRow {
            FocusChip(if (showKey) stringResource(R.string.media_hide_key) else stringResource(R.string.media_show_key), onClick = { showKey = !showKey })
            FocusChip(stringResource(R.string.settings_save_key), onClick = { onSave(draft); draft = "" }, enabled = draft.length >= 16)
            FocusChip(stringResource(R.string.settings_get_a_free_key), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SteamGridDb.KEY_PAGE.toUri())) } })
            if (!saved.isNullOrBlank()) FocusChip(stringResource(R.string.settings_remove_key), onClick = { onSave(null) })
        }
    }
}

@Composable
private fun EmulatorAppsCard() {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_other_emulator_apps))
        CardBody(
            stringResource(R.string.settings_as_well_as_the_built_in),
        )
        CardBody(
            stringResource(R.string.settings_to_add_a_whole_set_at),
        )
        ChipRow {
            FocusChip(stringResource(R.string.settings_get_obtainium), onClick = { open(StandaloneLauncher.OBTAINIUM_PAGE) })
            FocusChip(stringResource(R.string.settings_emulation_pack), onClick = { open(EMULATION_PACK_PAGE) })
        }
    }
}

/** Community list of emulators for Obtainium (Unlicense). */
private const val EMULATION_PACK_PAGE = "https://github.com/RJNY/Obtainium-Emulation-Pack/releases/latest"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotkeysCard(enabled: Boolean, menu: HotkeyCombo, quit: HotkeyCombo, onChange: (Boolean, HotkeyCombo, HotkeyCombo) -> Unit, onShowAll: () -> Unit) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_retroarch_buttons))
        if (enabled) {
            StatusPill(stringResource(R.string.settings_on), StatusTone.GOOD)
            CardBody(stringResource(R.string.settings_in_games_started_from_retroforge_quits, quit.label, menu.label))
        } else {
            StatusPill(stringResource(R.string.settings_off_retroarch_s_own_settings), StatusTone.NEUTRAL)
        }
        CardBody(
            stringResource(R.string.settings_button_combos_that_work_in_games),
        )
        if (enabled) {
            CardBody(stringResource(R.string.settings_menu))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HotkeyCombo.entries.filter { it != HotkeyCombo.NONE && it != quit }.forEach { c ->
                    FocusChip((if (c == menu) "● " else "") + c.label, onClick = { onChange(true, c, quit) })
                }
            }
            CardBody(stringResource(R.string.settings_quit_to_retroforge))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HotkeyCombo.entries.filter { it != HotkeyCombo.NONE && it != menu }.forEach { c ->
                    FocusChip((if (c == quit) "● " else "") + c.label, onClick = { onChange(true, menu, c) })
                }
            }
        }
        ChipRow {
            FocusChip(stringResource(R.string.settings_show_all_hotkeys), onClick = onShowAll)
            FocusChip(if (enabled) stringResource(R.string.settings_turn_hotkeys_off) else stringResource(R.string.settings_turn_hotkeys_on), onClick = { onChange(!enabled, menu, quit) })
        }
    }
}

@Composable
private fun ScreenScraperCard(savedUser: String?, onSave: (String?, String?) -> Unit) {
    val context = LocalContext.current
    var user by remember(savedUser) { mutableStateOf(savedUser.orEmpty()) }
    var password by remember(savedUser) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val available = BuildConfig.SCREENSCRAPER_DEV_ID.isNotBlank()
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_box_art_from_screenscraper))
        if (!available) {
            StatusPill(stringResource(R.string.settings_not_available_in_this_build), StatusTone.NEUTRAL)
            CardBody(stringResource(R.string.settings_screenscraper_needs_retroforge_s_own_developer))
        } else if (savedUser.isNullOrBlank()) {
            StatusPill(stringResource(R.string.settings_no_account_lower_limits), StatusTone.NEUTRAL)
        } else {
            StatusPill(stringResource(R.string.settings_signed_in_as, savedUser), StatusTone.GOOD)
        }
        CardBody(
            stringResource(R.string.settings_optional_sign_in_with_a_free),
        )
        ControllerTextField(value = user, onValueChange = { user = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.settings_screenscraper_username)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        ControllerTextField(
            value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(R.string.settings_password)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        ChipRow {
            FocusChip(if (showPassword) stringResource(R.string.network_drives_hide_password) else stringResource(R.string.network_drives_show_password), onClick = { showPassword = !showPassword })
            FocusChip(stringResource(R.string.settings_save), onClick = { onSave(user, password); password = "" }, enabled = user.isNotBlank() && password.isNotEmpty())
            FocusChip(stringResource(R.string.settings_create_a_free_account), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, ScreenScraper.SIGN_UP.toUri())) } })
            if (!savedUser.isNullOrBlank()) FocusChip(stringResource(R.string.settings_sign_out), onClick = { onSave(null, null) })
        }
    }
}

/** Opens the project's issue page with the app and device details filled in (no personal data). */
@Composable
private fun ReportProblemCard() {
    val context = LocalContext.current
    val url = BuildConfig.BUG_REPORT_URL
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        val body = "What happened:\n\nWhat you expected:\n\nSteps to repeat it:\n\n---\nRetroForge ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val intent = if (url.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, (url.trimEnd('/') + "/new?labels=bug&body=" + java.net.URLEncoder.encode(body, "UTF-8")).toUri())
        } else {
            // No issue tracker in this build: share the report by email, message or anything else.
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "RetroForge problem report").putExtra(Intent.EXTRA_TEXT, body),
                str(R.string.settings_send_the_report_with),
            )
        }
        runCatching { context.startActivity(intent) }
    }) {
        CardTitle(stringResource(R.string.settings_report_a_problem))
        CardBody(
            if (url.isNotBlank()) str(R.string.settings_opens_a_bug_report_on_github)
            else str(R.string.settings_writes_a_short_report_with_your),
        )
    }
}

/** View preference: DS games put their touch screen on a second display. */
const val DUAL_SCREEN_DS = "dual_screen_ds"

/** View preference: the second display shows the open game while browsing. */
const val DUAL_SCREEN_BROWSE = "dual_screen_browse"

/** Two-screen devices (AYN Thor, AYANEO Pocket DS …): what the second screen is used for. */
@Composable
private fun DualScreenCard(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val second = remember {
        context.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
    }
    var ds by remember { mutableStateOf(prefs.getBoolean(DUAL_SCREEN_DS, true)) }
    var browse by remember { mutableStateOf(prefs.getBoolean(DUAL_SCREEN_BROWSE, false)) }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        ds = !ds
        prefs.edit().putBoolean(DUAL_SCREEN_DS, ds).apply()
    }) {
        CardTitle(stringResource(R.string.settings_dual_screen))
        StatusPill(if (second != null) str(R.string.settings_second_screen_found, second.name) else str(R.string.settings_no_second_screen_on_this_device), if (second != null) StatusTone.GOOD else StatusTone.NEUTRAL)
        StatusPill(str(R.string.settings_ds_games_on_both_screens, if (ds) str(R.string.settings_on) else str(R.string.player_off)), if (ds) StatusTone.GOOD else StatusTone.NEUTRAL)
        StatusPill(str(R.string.settings_game_box_on_the_second_screen, if (browse) str(R.string.settings_on) else str(R.string.player_off)), if (browse) StatusTone.GOOD else StatusTone.NEUTRAL)
        com.retroforge.core.ui.FocusChip(stringResource(R.string.settings_switch_the_game_box), onClick = {
            browse = !browse
            prefs.edit().putBoolean(DUAL_SCREEN_BROWSE, browse).apply()
        })
        CardBody(
            stringResource(R.string.settings_for_two_screen_devices_like_the),
        )
    }
}

/** Netplay: the name other players see, and how many frames late buttons are applied (more for slow connections). */
@Composable
private fun NetplayCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(com.retroforge.app.player.Netplay.name(context)) }
    var delay by remember { mutableStateOf(com.retroforge.app.player.Netplay.delay(context)) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_play_together_netplay))
        CardBody(
            stringResource(R.string.settings_play_a_game_with_a_friend),
        )
        ControllerTextField(
            value = name,
            onValueChange = { name = it.take(24); com.retroforge.app.player.Netplay.setName(context, name) },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_your_name_for_other_players)) },
            modifier = Modifier.fillMaxWidth(),
        )
        FocusChip(stringResource(R.string.settings_input_delay_frames, delay), onClick = {
            val all = com.retroforge.app.player.Netplay.delays
            delay = all[(all.indexOf(delay) + 1) % all.size]
            com.retroforge.app.player.Netplay.setDelay(context, delay)
        })
        CardBody(stringResource(R.string.settings_buttons_work_this_many_frames_late))
    }
}

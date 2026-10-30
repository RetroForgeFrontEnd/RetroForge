package com.retroforge.app.ui.settings

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip

/** One topic: a title and its steps. Kept in step with docs/USER_GUIDE.md. */
private class Topic(val title: String, val steps: List<String>)

private val topics get() = listOf(
    Topic(str(R.string.settings_play_together_netplay), listOf(
        str(R.string.tutorial_two_players_on_two_devices_play),
        str(R.string.tutorial_to_host_open_the_game_and),
        str(R.string.tutorial_same_wi_fi_your_friend_opens),
        str(R.string.tutorial_over_the_internet_when_you_host),
        str(R.string.tutorial_input_delay_settings_playing_play_together),
        str(R.string.tutorial_during_netplay_both_devices_run_at),
        str(R.string.tutorial_play_together_shows_on_the_games),
    )),
    Topic(str(R.string.tutorial_getting_started), listOf(
        str(R.string.tutorial_reset_combo_use_with_care_hold),
        str(R.string.tutorial_settings_set_up_retroforge_walks_you),
        str(R.string.tutorial_your_games_folder_can_be_a),
        str(R.string.tutorial_it_can_be_on_the_phone),
        str(R.string.tutorial_new_games_retro_game_folders_scan),
        str(R.string.tutorial_settings_check_my_setup_shows_every),
        str(R.string.tutorial_retroforge_comes_with_no_games_and),
    )),
    Topic(str(R.string.tutorial_device_title), listOf(
        str(R.string.tutorial_device_intro),
        str(R.string.tutorial_device_low),
        str(R.string.tutorial_device_mid),
        str(R.string.tutorial_device_high),
        str(R.string.tutorial_device_flagship),
        str(R.string.tutorial_device_snapdragon),
        str(R.string.tutorial_device_ram),
    )),
    Topic(str(R.string.tutorial_games_and_films_on_a_network), listOf(
        str(R.string.tutorial_what_it_does_plays_games_and),
        str(R.string.tutorial_step_1_on_a_windows_pc),
        str(R.string.tutorial_step_2_on_the_pc_let),
        str(R.string.tutorial_step_2b_if_some_games_then),
        str(R.string.tutorial_step_3_choose_how_the_phone),
        str(R.string.tutorial_option_b_no_login_at_all),
        str(R.string.tutorial_a_nas_synology_qnap_and_others),
        str(R.string.tutorial_step_4_in_retroforge_settings_games),
        str(R.string.tutorial_tap_find_on_my_network_after),
        str(R.string.tutorial_shared_folder_the_share_name_from),
        str(R.string.tutorial_when_it_says_connected_it_s),
        str(R.string.tutorial_retroforge_scans_it_like_any_other),
        str(R.string.tutorial_films_media_choose_another_folder_then),
        str(R.string.tutorial_if_it_says_doesn_t_allow),
        str(R.string.tutorial_if_it_says_access_denied_or),
        str(R.string.tutorial_if_it_says_the_username_or),
        str(R.string.tutorial_if_find_on_my_network_finds),
        str(R.string.tutorial_cloud_storage_google_drive_dropbox_onedrive),
    )),
    Topic(str(R.string.tutorial_away_title), listOf(
        str(R.string.tutorial_away_what),
        str(R.string.tutorial_away_step1),
        str(R.string.tutorial_away_step2),
        str(R.string.tutorial_away_step3),
        str(R.string.tutorial_away_step4),
        str(R.string.tutorial_away_step5),
        str(R.string.tutorial_away_done),
        str(R.string.tutorial_away_speed),
        str(R.string.tutorial_away_data),
        str(R.string.tutorial_away_trouble),
    )),
    Topic(str(R.string.tutorial_finding_and_playing_games), listOf(
        str(R.string.tutorial_retro_shows_your_systems_only_ones),
        str(R.string.tutorial_open_a_game_and_press_play),
        str(R.string.tutorial_each_game_shows_how_long_you),
        str(R.string.tutorial_games_from_retrobat_folders_show_their),
        str(R.string.tutorial_home_surprise_me_opens_a_random),
        str(R.string.tutorial_big_games_discs_arcade_sets_play),
        str(R.string.tutorial_settings_playing_graphics_and_resolution_sets),
        str(R.string.tutorial_some_systems_need_bios_files_settings),
    )),
    Topic(str(R.string.tutorial_the_in_game_menu), listOf(
        str(R.string.tutorial_swipe_in_from_the_left_or),
        str(R.string.tutorial_the_first_page_resume_save_and),
        str(R.string.tutorial_picture_screen_shape_screen_filter_sharp),
        str(R.string.tutorial_sound_and_controls_volume_touch_controls),
        str(R.string.tutorial_widescreen_n64_and_dreamcast_switches_the),
        str(R.string.tutorial_cheats_pick_from_libretro_s_cheat),
        str(R.string.tutorial_carry_on_where_you_left_off),
    )),
    Topic(str(R.string.tutorial_touch_controls_controllers_and_remap_buttons), listOf(
        str(R.string.tutorial_on_screen_buttons_are_drawn_for),
        str(R.string.tutorial_connect_a_controller_and_the_on),
        str(R.string.tutorial_remap_buttons_in_game_menu_choose),
        str(R.string.tutorial_in_the_app_d_pad_to),
        str(R.string.tutorial_hold_r2_to_fast_forward_on),
    )),
    Topic(str(R.string.tutorial_computers_keyboard_mouse_and_bios), listOf(
        str(R.string.tutorial_the_built_in_player_also_runs),
        str(R.string.tutorial_keyboard_plug_in_or_pair_a),
        str(R.string.tutorial_mouse_amiga_atari_st_dos_scummvm),
        str(R.string.tutorial_dos_games_kept_as_a_folder),
        str(R.string.tutorial_commodore_disks_load_by_themselves_at),
        str(R.string.tutorial_bios_some_of_these_need_the),
    )),
    Topic(str(R.string.tutorial_other_emulators_retroarch_and_standalone_apps), listOf(
        str(R.string.tutorial_a_game_s_page_also_offers),
        str(R.string.tutorial_get_opens_obtainium_already_set_up),
        str(R.string.tutorial_once_installed_play_with_starts_the),
        str(R.string.tutorial_ps2_gamecube_wii_3ds_and_switch),
        str(R.string.tutorial_retroarch_hotkeys_start_select_quits_back),
    )),
    Topic(str(R.string.tutorial_graphics_drivers_and_texture_packs), listOf(
        str(R.string.tutorial_settings_gpu_and_drivers_shows_your),
        str(R.string.tutorial_texture_packs_n64_settings_download_n64),
        str(R.string.tutorial_your_own_packs_settings_texture_packs),
    )),
    Topic(str(R.string.tutorial_multi_disc_games), listOf(
        str(R.string.tutorial_a_game_with_an_m3u_playlist),
        str(R.string.tutorial_no_m3u_yet_rom_manager_multi),
    )),
    Topic(str(R.string.tutorial_covers_and_artwork), listOf(
        str(R.string.tutorial_artwork_from_your_other_frontend_gamelist),
        str(R.string.tutorial_open_a_game_and_choose_change),
        str(R.string.tutorial_steamgriddb_thousands_of_community_covers_add),
        str(R.string.tutorial_screenscraper_front_and_3d_boxes_an),
        str(R.string.tutorial_your_own_image_any_picture_on),
    )),
    Topic(str(R.string.tutorial_favourites_playlists_and_backups), listOf(
        str(R.string.tutorial_save_syncing_settings_playing_save_syncing),
        str(R.string.tutorial_on_a_game_s_page_add),
        str(R.string.tutorial_coming_from_es_de_settings_import),
        str(R.string.tutorial_settings_back_up_saves_and_settings),
        str(R.string.tutorial_playstation_memory_cards_settings_playing_pick),
    )),
    Topic(str(R.string.tutorial_rom_hacks_and_patches), listOf(
        str(R.string.tutorial_rom_hacks_shows_hacks_you_already),
        str(R.string.tutorial_download_and_patch_for_hacks_published),
        str(R.string.tutorial_rom_patcher_retro_screen_applies_ips),
    )),
    Topic(str(R.string.library_rom_manager), listOf(
        str(R.string.tutorial_the_only_part_of_retroforge_that),
        str(R.string.tutorial_duplicates_finds_the_same_game_in),
        str(R.string.tutorial_compress_playstation_saturn_dreamcast_and_other),
        str(R.string.tutorial_sort_moves_loose_games_into_the),
        str(R.string.tutorial_nothing_is_deleted_straight_away_files),
        str(R.string.tutorial_if_another_frontend_uses_the_same),
    )),
    Topic(str(R.string.tutorial_pc_ports_of_games_you_own), listOf(
        str(R.string.tutorial_pc_ports_lists_fan_made_pc),
        str(R.string.tutorial_android_tab_download_and_install_gets),
        str(R.string.tutorial_windows_tab_download_windows_version_unpacks),
        str(R.string.tutorial_prepare_files_copies_your_rom_where),
    )),
    Topic(str(R.string.tutorial_pc_windows_games), listOf(
        str(R.string.tutorial_your_pc_games_pc_tab_steam),
        str(R.string.tutorial_these_games_start_in_a_separate),
        str(R.string.tutorial_gamenative_install_it_from_pc_gamenative),
        str(R.string.tutorial_if_play_says_the_executable_couldn),
        str(R.string.tutorial_droiddeck_needs_a_snapdragon_chip_with),
        str(R.string.tutorial_use_only_games_you_own_retroforge),
    )),
    Topic(str(R.string.tutorial_pc_retroforge_s_own_pc_player), listOf(
        str(R.string.tutorial_retroforge_can_play_windows_games_itself),
        str(R.string.tutorial_add_a_game_pc_add_a),
        str(R.string.tutorial_where_games_are_kept_inside_retroforge),
        str(R.string.tutorial_controls_the_on_screen_controller_is),
        str(R.string.tutorial_wrong_program_starts_pc_change_a),
        str(R.string.tutorial_older_graphics_chips_if_a_game),
        str(R.string.tutorial_steam_cloud_saves_sync_before_and),
    )),
    Topic(str(R.string.tutorial_media_films_and_3d), listOf(
        str(R.string.tutorial_media_use_retroforge_s_film_folders),
        str(R.string.tutorial_copy_films_in_plug_the_phone),
        str(R.string.tutorial_already_keep_films_somewhere_else_an),
        str(R.string.tutorial_films_show_as_a_poster_grid),
        str(R.string.tutorial_3d_films_their_file_name_needs),
        str(R.string.tutorial_to_watch_in_3d_open_the),
        str(R.string.tutorial_if_a_film_can_t_play),
        str(R.string.tutorial_plex_jellyfin_emby_or_a_nas),
        str(R.string.tutorial_plex_not_found_in_plex_on),
    )),
    Topic("VR", listOf(
        str(R.string.tutorial_vr_tab_play_in_a_cardboard),
        str(R.string.tutorial_switch_vr_on_from_the_in),
        str(R.string.tutorial_meta_quest_retroforge_runs_as_a),
    )),
    Topic(str(R.string.tutorial_dual_screens_ayn_thor_ayaneo_pocket), listOf(
        str(R.string.tutorial_settings_dual_screen_ds_games_the),
        str(R.string.tutorial_game_box_on_the_second_screen),
    )),
    Topic(str(R.string.tutorial_android_apps_and_launcher_mode), listOf(
        str(R.string.tutorial_the_android_tab_lists_every_app),
        str(R.string.tutorial_launcher_mode_makes_retroforge_the_home),
    )),
    Topic(str(R.string.tutorial_looks_sounds_and_help), listOf(
        str(R.string.tutorial_settings_themes_colours_shapes_type_and),
        str(R.string.tutorial_interface_sounds_a_tick_when_moving),
        str(R.string.tutorial_settings_report_a_problem_shares_the),
        str(R.string.tutorial_links_has_the_youtube_channels_and),
    )),
)

@Composable
fun TutorialScreen() {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    var openTopic by remember { mutableStateOf<String?>(null) }

    ScreenFrame(title = stringResource(R.string.settings_how_to_use_retroforge), subtitle = stringResource(R.string.tutorial_grab_a_drink_and_a_snack)) {
        topics.forEach { topic ->
            val expanded = openTopic == topic.title
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { openTopic = if (expanded) null else topic.title }) {
                CardTitle((if (expanded) "▾ " else "▸ ") + topic.title)
                if (expanded) topic.steps.forEach { CardBody("• $it") }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.tutorial_learn_more_about_android_emulation))
            CardBody(
                stringResource(R.string.tutorial_retro_game_corps_russ_writes_some),
            )
        }
        FocusChip(stringResource(R.string.tutorial_android_emulation_starter_guide), onClick = { open(STARTER_GUIDE) })
        FocusChip(stringResource(R.string.tutorial_dual_screen_android_handheld_guide), onClick = { open(DUAL_SCREEN_GUIDE) })
        FocusChip(stringResource(R.string.tutorial_all_retro_game_corps_guides), onClick = { open(ALL_GUIDES) })
    }
}

private const val STARTER_GUIDE = "https://retrogamecorps.com/2022/03/13/android-emulation-starter-guide/"
private const val DUAL_SCREEN_GUIDE = "https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/"
private const val ALL_GUIDES = "https://retrogamecorps.com/guides/"

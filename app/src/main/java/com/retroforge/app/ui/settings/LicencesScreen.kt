package com.retroforge.app.ui.settings

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard

private class Notice(val name: String, val licence: String, val detail: String, val asset: String? = null)

/** Shout-outs: the projects and people RetroForge uses, opens or features. Kept in step with the README's Thanks. */
private val thanks get() = listOf(
    str(R.string.licences_windows_games_on_android) to str(R.string.licences_gamenative_utkarshdalal_winlator_brunodev85_and_droiddeck),
    str(R.string.licences_libretro_and_retroarch) to str(R.string.licences_the_libretro_api_and_the_team),
    str(R.string.hotkeys_standalone_emulators) to str(R.string.licences_dolphin_nethersx2_armsx2_azahar_eden_m64plus),
    str(R.string.licences_pc_ports_and_recompilations) to str(R.string.licences_ship_of_harkinian_2_ship_2),
    str(R.string.licences_collections_and_frontends) to str(R.string.licences_retrobat_and_es_de_whose_folder),
    str(R.string.licences_artwork_and_game_data) to str(R.string.licences_steamgriddb_screenscraper_and_libretro_thumbnails_for),
    str(R.string.licences_gpu_drivers) to str(R.string.licences_the_mesa_turnip_developers_k11mch1_adrenotoolsdrivers),
    str(R.string.licences_the_community) to str(R.string.licences_the_youtube_channels_and_sites_in),
    str(R.string.licences_built_with_claude) to str(R.string.licences_retroforge_was_made_by_darren_glancy),
)

/** Kept in step with docs/THIRD_PARTY_LICENCES.md. */
private val notices get() = listOf(
    Notice("Android Jetpack (Compose, Navigation, Lifecycle, Room, DataStore, Core)", "Apache License 2.0", "Copyright The Android Open Source Project."),
    Notice("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0", "Copyright JetBrains s.r.o. and contributors."),
    Notice("Coil", "Apache License 2.0", "Copyright Coil Contributors."),
    Notice("Apache Commons Compress", "Apache License 2.0", "Copyright The Apache Software Foundation."),
    Notice("XZ for Java", "0BSD", "By Lasse Collin and contributors."),
    Notice(
        "ES-DE Frontend data (emulator launch commands, package names, ScreenScraper system ids)", "MIT",
        "Copyright (c) 2024-2026 Northwestern Software AB, (c) 2020-2024 Leon Styhre, (c) 2014 Alec Lofquist. Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the Software without restriction, subject to including this notice. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
    ),
    Notice(
        str(R.string.licences_libretro_api_header_libretro_h_used), "MIT",
        "Copyright (C) 2010-2024 The RetroArch team. Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the Software without restriction, subject to including this notice. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
    ),
    Notice(
        str(R.string.licences_libadrenotools_and_linkernsbypass_loading_turnip_drivers), "BSD 2-Clause",
        "Copyright (c) 2021, Billy Laws. Redistribution and use in source and binary forms, with or without modification, are permitted provided that: redistributions of source code retain the above copyright notice, this list of conditions and the following disclaimer; redistributions in binary form reproduce them in the documentation and/or other materials provided with the distribution. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED; IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE.",
    ),
    Notice(
        str(R.string.licences_better_xcloud_userscript_pc_tab_xbox), "MIT",
        "Copyright (c) 2023 redphx, (c) 2020 Phosphor Icons. github.com/redphx/better-xcloud, bundled unchanged and added to Microsoft's xbox.com/play page. Not affiliated with Xbox.",
        "licences/better-xcloud-MIT.txt",
    ),
    Notice(
        str(R.string.licences_emulator_cores_mgba_snes9x_genesis_plus), str(R.string.licences_each_core_s_own_licence),
        str(R.string.licences_not_part_of_retroforge_downloaded_from),
    ),
    Notice(
        str(R.string.licences_canvas_es_system_artwork_retro_tab), "CC0 1.0",
        str(R.string.licences_by_siddy212_github_com_siddy212_canvas),
    ),
    Notice(
        str(R.string.licences_the_bezel_project_bezels_settings_covers), str(R.string.licences_the_bezel_project_s_own_terms),
        str(R.string.licences_bezel_artwork_by_the_bezel_project),
    ),
    Notice(
        str(R.string.licences_libretro_cheat_database_pause_menu_cheats), "CC BY-SA 4.0",
        str(R.string.licences_github_com_libretro_libretro_database_not),
    ),
    Notice(str(R.string.licences_press_start_2p_font), "SIL Open Font License 1.1", "Copyright 2012 The Press Start 2P Project Authors.", "licences/font-pressstart2p-OFL.txt"),
    Notice(str(R.string.licences_orbitron_font), "SIL Open Font License 1.1", "Copyright The Orbitron Project Authors.", "licences/font-orbitron-OFL.txt"),
    Notice(str(R.string.licences_rajdhani_font), "SIL Open Font License 1.1", "Copyright Indian Type Foundry.", "licences/font-rajdhani-OFL.txt"),
    Notice(str(R.string.licences_exo_2_font), "SIL Open Font License 1.1", "Copyright The Exo 2 Project Authors.", "licences/font-exo2-OFL.txt"),
)

@Composable
fun LicencesScreen() {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    ScreenFrame(title = stringResource(R.string.settings_thanks_and_licences), subtitle = stringResource(R.string.licences_retroforge_stands_on_the_work_of)) {
        thanks.forEach { (who, what) ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(who)
                CardBody(what)
            }
        }
        CardTitle(stringResource(R.string.licences_licences))
        CardBody(stringResource(R.string.licences_retroforge_is_built_with_these_open))
        notices.forEach { n ->
            val expanded = open == n.name
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { open = if (expanded) null else n.name }) {
                CardTitle(n.name)
                CardBody("${n.licence}. ${n.detail}")
                if (expanded && n.asset != null) {
                    val text = remember(n.asset) { runCatching { context.assets.open(n.asset).bufferedReader().use { it.readText() } }.getOrNull() }
                    CardBody(text ?: stringResource(R.string.licences_licence_text_unavailable))
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.licences_not_included))
            CardBody(
                stringResource(R.string.licences_retroarch_standalone_emulators_droiddeck_and_obtainium),
            )
        }
    }
}

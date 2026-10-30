package com.retroforge.app.ui.settings

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
private val thanks = listOf(
    "Windows games on Android" to "GameNative (utkarshdalal), Winlator (brunodev85) and DroidDeck: the apps that make PC games " +
        "play on a phone. RetroForge opens them; all the hard work is theirs.",
    "libretro and RetroArch" to "The libretro API and the team and core authors behind every emulator the built-in player runs: " +
        "mGBA, Snes9x, Genesis Plus GX, FCEUmm, Mupen64Plus-Next, PCSX ReARMed, SwanStation, PPSSPP, Flycast, Dolphin, LRPS2, " +
        "FinalBurn Neo, MAME and many more. Also the libretro thumbnails and cheat database.",
    "Standalone emulators" to "Dolphin, NetherSX2, ARMSX2, Azahar, Eden, M64Plus FZ, melonDS, MAME4droid, SkyEmu, NooDS, " +
        "EX Emulators, aPS3e, J2ME Loader and the rest of the list RetroForge offers.",
    "PC ports and recompilations" to "Ship of Harkinian, 2 Ship 2 Harkinian, Starship, SpaghettiKart, Ghostship, Zelda64Recomp, " +
        "Unleashed Recompiled, OpenGOAL, DevilutionX, OpenMW, OpenBOR, the Perfect Dark and zelda3 ports, and everyone who " +
        "brought them to Android.",
    "Collections and frontends" to "RetroBat and ES-DE, whose folder layouts RetroForge reads as they are; Obtainium (ImranR98) " +
        "and the Obtainium Emulation Pack (RJNY) for keeping emulators up to date; canvas-es by Siddy212 for the system artwork.",
    "Artwork and game data" to "SteamGridDB, ScreenScraper and libretro thumbnails for covers; Romhacking.net and every hack " +
        "author for the ROM hack catalogue.",
    "GPU drivers" to "The Mesa Turnip developers, K11MCH1 (AdrenoToolsDrivers) and MrPurple (Purple Turnip).",
    "The community" to "The YouTube channels and sites in the Links tab, for the guides, reviews and handheld coverage that " +
        "got so many people into retro handhelds.",
    "Built with Claude" to "RetroForge was made by Darren Glancy with a lot of help from Anthropic's Claude.",
)

/** Kept in step with docs/THIRD_PARTY_LICENCES.md. */
private val notices = listOf(
    Notice("Android Jetpack (Compose, Navigation, Lifecycle, Room, DataStore, Core)", "Apache License 2.0", "Copyright The Android Open Source Project."),
    Notice("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0", "Copyright JetBrains s.r.o. and contributors."),
    Notice("Coil", "Apache License 2.0", "Copyright Coil Contributors."),
    Notice("Apache Commons Compress", "Apache License 2.0", "Copyright The Apache Software Foundation."),
    Notice("XZ for Java", "0BSD", "By Lasse Collin and contributors."),
    Notice(
        "ES-DE Frontend data (emulator launch commands, package names, ScreenScraper system ids)", "MIT",
        "Copyright (c) 2024-2026 Northwestern Software AB, (c) 2020-2024 Leon Styhre, (c) 2014 Alec Lofquist. " +
            "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, " +
            "to deal in the Software without restriction, subject to including this notice. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
    ),
    Notice(
        "libretro API header (libretro.h), used by the built-in player", "MIT",
        "Copyright (C) 2010-2024 The RetroArch team. Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
            "software and associated documentation files, to deal in the Software without restriction, subject to including this notice. " +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
    ),
    Notice(
        "libadrenotools and linkernsbypass (loading Turnip drivers in the built-in player)", "BSD 2-Clause",
        "Copyright (c) 2021, Billy Laws. Redistribution and use in source and binary forms, with or without modification, are permitted " +
            "provided that: redistributions of source code retain the above copyright notice, this list of conditions and the following " +
            "disclaimer; redistributions in binary form reproduce them in the documentation and/or other materials provided with the " +
            "distribution. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED " +
            "WARRANTIES ARE DISCLAIMED; IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING IN ANY WAY " +
            "OUT OF THE USE OF THIS SOFTWARE.",
    ),
    Notice(
        "Better xCloud userscript (PC tab → Xbox Cloud Gaming)", "MIT",
        "Copyright (c) 2023 redphx, (c) 2020 Phosphor Icons. github.com/redphx/better-xcloud, bundled unchanged and added to " +
            "Microsoft's xbox.com/play page. Not affiliated with Xbox.",
        "licences/better-xcloud-MIT.txt",
    ),
    Notice(
        "Emulator cores (mGBA, Snes9x, Genesis Plus GX, FCEUmm, Gambatte, DeSmuME and others)", "Each core's own licence",
        "Not part of RetroForge: downloaded from libretro's build server (buildbot.libretro.com) when needed and loaded by the built-in player. " +
            "Some are licensed for non-commercial use only, one reason RetroForge is free.",
    ),
    Notice(
        "canvas-es system artwork (Retro tab)", "CC0 1.0",
        "By Siddy212, github.com/Siddy212/canvas-es. Not part of RetroForge: downloaded from that repository for the systems in your " +
            "library. Console and game names, logos and characters shown in it belong to their owners.",
    ),
    Notice(
        "The Bezel Project bezels (Settings → Covers and bezels → Bezels)", "The Bezel Project's own terms",
        "Bezel artwork by The Bezel Project, github.com/thebezelproject. Not part of RetroForge: a system's bezels are downloaded " +
            "from their GitHub repositories when you ask, for your own use. Console and game names, logos and art shown in them belong to their owners.",
    ),
    Notice(
        "libretro cheat database (pause menu → Cheats)", "CC BY-SA 4.0",
        "github.com/libretro/libretro-database. Not part of RetroForge: a game's cheat file is downloaded from there when you open its Cheats page.",
    ),
    Notice("Press Start 2P font", "SIL Open Font License 1.1", "Copyright 2012 The Press Start 2P Project Authors.", "licences/font-pressstart2p-OFL.txt"),
    Notice("Orbitron font", "SIL Open Font License 1.1", "Copyright The Orbitron Project Authors.", "licences/font-orbitron-OFL.txt"),
    Notice("Rajdhani font", "SIL Open Font License 1.1", "Copyright Indian Type Foundry.", "licences/font-rajdhani-OFL.txt"),
    Notice("Exo 2 font", "SIL Open Font License 1.1", "Copyright The Exo 2 Project Authors.", "licences/font-exo2-OFL.txt"),
)

@Composable
fun LicencesScreen() {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    ScreenFrame(title = "Thanks and licences", subtitle = "RetroForge stands on the work of a lot of people. Thank you all.") {
        thanks.forEach { (who, what) ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(who)
                CardBody(what)
            }
        }
        CardTitle("Licences")
        CardBody("RetroForge is built with these open-source parts. Select one to read its licence text where it ships with the app.")
        notices.forEach { n ->
            val expanded = open == n.name
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { open = if (expanded) null else n.name }) {
                CardTitle(n.name)
                CardBody("${n.licence}. ${n.detail}")
                if (expanded && n.asset != null) {
                    val text = remember(n.asset) { runCatching { context.assets.open(n.asset).bufferedReader().use { it.readText() } }.getOrNull() }
                    CardBody(text ?: "Licence text unavailable.")
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Not included")
            CardBody(
                "RetroArch, standalone emulators, DroidDeck and Obtainium are separate apps with their own licences; RetroForge only opens them. " +
                    "No games, BIOS or firmware are included. Covers come from libretro, SteamGridDB or ScreenScraper when you ask, under their own terms.",
            )
        }
    }
}

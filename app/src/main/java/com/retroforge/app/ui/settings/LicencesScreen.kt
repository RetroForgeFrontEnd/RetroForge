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
        "Emulator cores (mGBA, Snes9x, Genesis Plus GX, FCEUmm, Gambatte, DeSmuME and others)", "Each core's own licence",
        "Not part of RetroForge: downloaded from libretro's build server (buildbot.libretro.com) when needed and loaded by the built-in player. " +
            "Some are licensed for non-commercial use only, one reason RetroForge is free.",
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
    ScreenFrame(title = "Licences", subtitle = "RetroForge is built with these open-source parts. Select one to read its licence text where it ships with the app.") {
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

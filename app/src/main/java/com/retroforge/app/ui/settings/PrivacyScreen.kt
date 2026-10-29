package com.retroforge.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard

/** Mirrors docs/PRIVACY_POLICY.md. */
@Composable
fun PrivacyScreen() {
    ScreenFrame(title = "Privacy", subtitle = "RetroForge collects nothing about you: no accounts, no analytics, no ads, no tracking.") {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("On your device only")
            CardBody("Your library, play history, favourites, playlists, covers, settings, themes, ROM Manager history and any keys or logins you add stay in RetroForge's private storage. Uninstalling deletes them.")
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Internet, only when you ask")
            CardBody("• libretro thumbnails, for box art: system and game names.")
            CardBody("• SteamGridDB, when you search covers: your search and your own API key.")
            CardBody("• ScreenScraper, when you look a game up: its file name, size and checksum, RetroForge's developer ID and your login if you added one.")
            CardBody("• Hack catalogues you added, when you refresh them.")
            CardBody("Links you open (emulator pages, hack pages, videos) go to your browser.")
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Your files")
            CardBody("RetroForge reads the folders you choose and changes files only when you confirm a ROM Manager action. Nothing is uploaded.")
        }
    }
}

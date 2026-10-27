package com.retroforge.app.ui.retro

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone

@Composable
fun RetroScreen() {
    ScreenFrame(title = "Retro", subtitle = "Your consoles, arcade and computer games.") {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Game library")
            StatusPill("Not built yet", StatusTone.NEUTRAL)
            CardBody(
                "Adding game folders and scanning them is the next milestone. " +
                    "There is nothing to launch in this build.",
            )
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Planned: your RetroBat collection")
            CardBody(
                "RetroForge will recognise RetroBat and ES-DE folder names (snes, megadrive, psx, …) and can read " +
                    "existing gamelist.xml files for titles, descriptions and artwork, without changing them.",
            )
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Planned: emulators")
            CardBody(
                "Games will open in emulators you install separately, starting with RetroArch for SNES. " +
                    "Each system will be marked as launch-tested only after it has actually been tested on a device.",
            )
        }
    }
}

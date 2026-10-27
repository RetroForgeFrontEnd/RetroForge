package com.retroforge.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.Section
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.Wordmark

@Composable
fun HomeScreen(showPc: Boolean, onOpen: (Section) -> Unit) {
    ScreenFrame(title = null) {
        Wordmark()
        Text(
            "Merging the past, building the future.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusPill("Preview build ${BuildConfig.VERSION_NAME}: no games can be launched yet", StatusTone.CAUTION)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FocusCard(modifier = Modifier.weight(1f), onClick = { onOpen(Section.RETRO) }) {
                CardTitle("Retro")
                CardBody("Consoles, arcade and classic computers.")
                Column(Modifier.padding(top = 12.dp)) {
                    StatusPill("Library scanning: next milestone", StatusTone.NEUTRAL)
                }
            }
            if (showPc) {
                FocusCard(modifier = Modifier.weight(1f), onClick = { onOpen(Section.PC) }) {
                    CardTitle("PC")
                    CardBody("Windows games through a separately installed DroidDeck.")
                    Column(Modifier.padding(top = 12.dp)) {
                        StatusPill("Check this device", StatusTone.NEUTRAL)
                    }
                }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Your files stay yours")
            CardBody(
                "RetroForge reads your game folders in place. It never renames, moves or deletes ROMs, " +
                    "and keeps its own data separate so RetroBat and ES-DE keep working with the same collection.",
            )
        }
    }
}

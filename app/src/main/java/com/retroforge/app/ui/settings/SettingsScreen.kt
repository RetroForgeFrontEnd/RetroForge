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
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.legalNoticeParagraphs
import com.retroforge.core.settings.ThemeMode
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenSetup: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ScreenFrame(title = "Settings") {
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSetup) {
            CardTitle("Set up RetroForge")
            CardBody("Games folder, RetroArch, the cores your games need, and your BIOS files.")
        }

        CardTitle("Appearance")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeMode.entries.forEach { mode ->
                val selected = settings.themeMode == mode
                FocusCard(
                    modifier = Modifier.weight(1f),
                    selected = selected,
                    onClick = { viewModel.setThemeMode(mode) },
                ) {
                    Text(
                        text = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.LIGHT -> "Light"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (selected) StatusPill("In use", StatusTone.GOOD)
                }
            }
        }

        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.setShowPcSection(!settings.showPcSection) },
        ) {
            CardTitle("Show PC section")
            StatusPill(if (settings.showPcSection) "On" else "Off", if (settings.showPcSection) StatusTone.GOOD else StatusTone.NEUTRAL)
            CardBody("Hide the PC section if you only play retro games or this device cannot run PC games.")
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Games, BIOS and the law")
            legalNoticeParagraphs.forEach { CardBody(it) }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("About")
            CardBody("RetroForge ${BuildConfig.VERSION_NAME}")
            CardBody("Controller: D-pad to move, A to select, B to go back, L1/R1 to switch sections.")
        }
    }
}

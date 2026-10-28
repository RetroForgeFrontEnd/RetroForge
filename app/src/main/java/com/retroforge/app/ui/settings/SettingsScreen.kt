package com.retroforge.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.net.toUri
import com.retroforge.artwork.SteamGridDb
import com.retroforge.core.ui.FocusChip
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
fun SettingsScreen(viewModel: SettingsViewModel, onOpenSetup: () -> Unit, onOpenAbout: () -> Unit) {
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

        SteamGridDbKeyCard(settings.steamGridDbKey, viewModel::setSteamGridDbKey)

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Games, BIOS and the law")
            legalNoticeParagraphs.forEach { CardBody(it) }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAbout) {
            CardTitle("About RetroForge")
            CardBody("Version ${BuildConfig.VERSION_NAME}. Select to read what RetroForge is for.")
            CardBody("Controller: D-pad to move, A to select, B to go back, L1/R1 to switch sections.")
        }
    }
}

@Composable
private fun SteamGridDbKeyCard(saved: String?, onSave: (String?) -> Unit) {
    val context = LocalContext.current
    var draft by remember(saved) { mutableStateOf("") }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Custom covers (SteamGridDB)")
        if (saved.isNullOrBlank()) {
            StatusPill("No API key", StatusTone.NEUTRAL)
            CardBody(
                "SteamGridDB has thousands of community-made covers. Create a free account, copy your API key from " +
                    "${SteamGridDb.KEY_PAGE} and paste it here. It stays on this device and is sent only to SteamGridDB.",
            )
        } else {
            StatusPill("API key saved (…${saved.takeLast(4)})", StatusTone.GOOD)
            CardBody("Use \"Change cover\" on any game to browse SteamGridDB covers.")
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            singleLine = true,
            label = { Text(if (saved.isNullOrBlank()) "Paste API key" else "Replace API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusChip("Save key", onClick = { onSave(draft); draft = "" }, enabled = draft.length >= 16)
        FocusChip("Get a free key", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SteamGridDb.KEY_PAGE.toUri())) } })
        if (!saved.isNullOrBlank()) FocusChip("Remove key", onClick = { onSave(null) })
    }
}

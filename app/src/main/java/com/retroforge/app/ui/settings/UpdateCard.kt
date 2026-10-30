package com.retroforge.app.ui.settings

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.AppUpdate
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.app.ui.DownloadBar
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** On Home: appears only when the daily check finds a newer RetroForge. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AppUpdate.Available?>(null) }
    LaunchedEffect(Unit) {
        update = withContext(Dispatchers.IO) { AppUpdate.checkIfDue(context) }
        update?.let { android.widget.Toast.makeText(context, "RetroForge ${it.version} is out: update from Home", android.widget.Toast.LENGTH_LONG).show() }
    }
    update?.let { found -> UpdateOffer(found, onDismiss = { update = null }) }
}

/** Settings → Help and about: check now, and whether RetroForge checks by itself. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(AppUpdate.autoCheck(context)) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<AppUpdate.Available?>(null) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.update_updates)) { StatusPill(BuildConfig.VERSION_NAME, StatusTone.NEUTRAL) }
        CardBody(stringResource(R.string.update_new_versions_come_from_retroforge_s))
        ChipRow {
            FocusChip(stringResource(R.string.update_check_for_updates), enabled = !checking, onClick = {
                checking = true
                result = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { AppUpdate.check() } }
                    found = r.getOrNull()
                    result = r.exceptionOrNull()?.let { "Couldn't check: ${it.message}" } ?: if (found == null) "You have the newest version." else null
                    checking = false
                }
            })
            FocusChip(if (auto) "Check automatically: on" else "Check automatically: off", onClick = {
                auto = !auto
                AppUpdate.prefs(context).edit().putBoolean("auto", auto).apply()
            })
        }
        if (checking) DownloadBar(null)
        result?.let { CardBody(it) }
    }
    found?.let { UpdateOffer(it, onDismiss = { found = null }) }
}

@Composable
private fun UpdateOffer(update: AppUpdate.Available, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("RetroForge ${update.version} is out") { StatusPill(stringResource(R.string.hacks_update), StatusTone.GOOD) }
        if (update.notes.isNotBlank()) CardBody(update.notes)
        ChipRow {
            FocusChip(if (downloading) "Downloading…" else "Download and install", enabled = !downloading, onClick = {
                downloading = true
                problem = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { AppUpdate.install(context, update) { progress = it / 100f } } }
                    problem = r.exceptionOrNull()?.let { "Couldn't download it: ${it.message}" }
                    downloading = false
                }
            })
            if (!downloading) {
                FocusChip(stringResource(R.string.pc_games_not_now), onClick = onDismiss)
                FocusChip(stringResource(R.string.update_skip_this_version), onClick = {
                    AppUpdate.prefs(context).edit().putString("skipped", update.version).apply()
                    onDismiss()
                })
            }
        }
        if (downloading) DownloadBar(progress)
        problem?.let { CardBody(it) }
    }
}

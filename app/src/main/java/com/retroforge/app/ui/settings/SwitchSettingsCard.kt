package com.retroforge.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.str

/** The RetroForge Switch Player's own settings screen (keys, firmware, overlay, GPU driver); it has no app icon. */
object SwitchPlayer {
    const val PACKAGE = "com.retroforge.switchplayer"
    private const val SETTINGS = "org.yuzu.yuzu_emu.ui.main.MainActivity"

    fun installed(context: Context): Boolean = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** [setup]: only keys and firmware, plus Done. [check]: shows nothing and answers straight away. */
    fun settings(setup: Boolean = false, check: Boolean = false): Intent = Intent().setClassName(PACKAGE, SETTINGS).apply {
        if (setup) putExtra("setup", true)
        if (check) putExtra("check", true)
    }
}

/** What the player reported: RESULT_OK only when both keys and firmware are installed. */
private data class SwitchReady(val keys: Boolean, val firmware: Boolean, val version: String?)

private fun ActivityResult.toReady(): SwitchReady? = data?.let {
    SwitchReady(it.getBooleanExtra("keys", false), it.getBooleanExtra("firmware", false), it.getStringExtra("firmware_version"))
} ?: if (resultCode == Activity.RESULT_OK) SwitchReady(true, true, null) else null

/**
 * Opens the Switch Player's settings: in [setup] mode just keys and firmware (Set up RetroForge),
 * otherwise the full screen (Settings). Shows whether keys and firmware are in, asked once when shown and after each visit.
 */
@Composable
fun SwitchSettingsCard(title: String, setup: Boolean) {
    val context = LocalContext.current
    val installed = remember { SwitchPlayer.installed(context) }
    var ready by remember { mutableStateOf<SwitchReady?>(null) }
    val check = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { ready = it.toReady() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ready = it.toReady() ?: ready
        runCatching { check.launch(SwitchPlayer.settings(check = true)) }
    }
    LaunchedEffect(installed) { if (installed) runCatching { check.launch(SwitchPlayer.settings(check = true)) } }

    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = if (installed) ({ runCatching { open.launch(SwitchPlayer.settings(setup = setup)) } }) else null) {
        val r = ready
        // No pill until the player has answered: CardTitle expects its status to draw something.
        CardTitle(title, status = if (installed && r == null) null else ({
            when {
                !installed -> StatusPill(stringResource(R.string.pc_not_installed), StatusTone.CAUTION)
                r == null -> Unit
                r.keys && r.firmware -> StatusPill(r.version?.let { str(R.string.switch_settings_firmware_version, it) } ?: stringResource(R.string.pc_stream_ready), StatusTone.GOOD)
                !r.keys -> StatusPill(stringResource(R.string.switch_settings_keys_missing), StatusTone.CAUTION)
                else -> StatusPill(stringResource(R.string.switch_settings_firmware_missing), StatusTone.CAUTION)
            }
        }))
        CardBody(
            when {
                !installed -> stringResource(R.string.switch_settings_install_player_first)
                setup -> stringResource(R.string.switch_settings_setup_body)
                else -> stringResource(R.string.switch_settings_body)
            },
        )
    }
}

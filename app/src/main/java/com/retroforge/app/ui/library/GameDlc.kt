package com.retroforge.app.ui.library

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.str

/**
 * DLC for players that install it in the background: the PS3 Player takes a .pkg (and its .rap licence, if it has one).
 * The player shows only Android's progress notification and reports back by broadcast.
 */
object GameDlc {
    private const val PS3 = "com.retroforge.ps3player"
    private const val PS3_INSTALL = "com.retroforge.ps3player.action.INSTALL_DLC"
    private const val PS3_PROGRESS = "com.retroforge.ps3player.DLC_PROGRESS"

    fun supported(context: Context, system: String): Boolean =
        system == "ps3" && runCatching { context.packageManager.getPackageInfo(PS3, 0) }.isSuccess

    /** Starts the install of [pkg], with [rap] as its licence when given. */
    fun installPs3(context: Context, pkg: Uri, rap: Uri?) {
        val intent = Intent(PS3_INSTALL).setClassName(PS3, "$PS3.DlcInstallService").setData(pkg)
            .putExtra("reply_package", context.packageName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Both files go in the ClipData so the player may read the licence too.
        intent.clipData = ClipData.newRawUri("pkg", pkg).also { clip -> rap?.let { clip.addItem(ClipData.Item(it)) } }
        rap?.let { intent.putExtra("rap", it) }
        ContextCompat.startForegroundService(context, intent)
    }

    fun progressFilter() = IntentFilter(PS3_PROGRESS)

    /** What to show for one progress broadcast. */
    fun describe(intent: Intent): String = when (intent.getStringExtra("state")) {
        "queued" -> str(R.string.dlc_queued)
        "running" -> str(R.string.dlc_installing, intent.getIntExtra("percent", 0))
        "done" -> str(R.string.dlc_done, intent.getStringExtra("title_id").orEmpty()) +
            if (intent.getBooleanExtra("rap_installed", false)) " " + str(R.string.dlc_licence_added) else ""
        else -> when (intent.getStringExtra("error")) {
            "not_a_pkg" -> str(R.string.dlc_not_a_pkg)
            "bad_rap" -> str(R.string.dlc_bad_rap)
            "no_space" -> str(R.string.dlc_no_space)
            else -> str(R.string.dlc_failed, intent.getStringExtra("message").orEmpty())
        }
    }
}

/** The game page's DLC card, for systems whose player installs DLC. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDlcCard(system: String) {
    val context = LocalContext.current
    if (!remember(system) { GameDlc.supported(context, system) }) return
    var status by remember { mutableStateOf<String?>(null) }
    var pkg by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { status = GameDlc.describe(intent) }
        }
        ContextCompat.registerReceiver(context, receiver, GameDlc.progressFilter(), ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val pickRap = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { rap ->
        rap ?: return@rememberLauncherForActivityResult
        pkg?.let {
            runCatching { GameDlc.installPs3(context, it, rap) }
                .onSuccess { status = str(R.string.dlc_queued) }
                .onFailure { e -> status = str(R.string.dlc_failed, e.message.orEmpty()) }
        }
        pkg = null
    }
    val pickPkg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pkg = uri
        status = str(R.string.dlc_pick_rap)
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.dlc_title))
        CardBody(stringResource(R.string.dlc_body_ps3))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pkg == null) {
                FocusChip(stringResource(R.string.dlc_add), onClick = { pickPkg.launch(arrayOf("*/*")) })
            } else {
                FocusChip(stringResource(R.string.dlc_choose_rap), onClick = { pickRap.launch(arrayOf("*/*")) })
                FocusChip(stringResource(R.string.dlc_no_rap), onClick = {
                    val chosen = pkg ?: return@FocusChip
                    runCatching { GameDlc.installPs3(context, chosen, null) }
                        .onSuccess { status = str(R.string.dlc_queued) }
                        .onFailure { e -> status = str(R.string.dlc_failed, e.message.orEmpty()) }
                    pkg = null
                })
            }
        }
        status?.let { CardBody(it) }
    }
}

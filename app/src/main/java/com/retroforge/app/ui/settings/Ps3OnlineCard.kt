package com.retroforge.app.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.str

/**
 * PS3 online play through RPCN, the free stand-in for PSN that RPCS3 uses: the RetroForge PS3 Player's RpcnService makes
 * the account, signs in and out, and answers by broadcast. RetroForge keeps no password: it goes once in the intent.
 */
object Ps3Online {
    private const val PLAYER = "com.retroforge.ps3player"
    private const val ACTION = "$PLAYER.action.RPCN"
    const val STATUS = "$PLAYER.RPCN_STATUS"

    fun installed(context: Context) = runCatching { context.packageManager.getPackageInfo(PLAYER, 0) }.isSuccess

    fun send(context: Context, op: String, vararg extras: Pair<String, String>) {
        val intent = Intent(ACTION).setClassName(PLAYER, "$PLAYER.RpcnService")
            .putExtra("op", op).putExtra("reply_package", context.packageName)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        ContextCompat.startForegroundService(context, intent)
    }

    /** What to show for one reply. */
    fun describe(intent: Intent): String {
        val npid = intent.getStringExtra("npid").orEmpty()
        if (intent.getBooleanExtra("ok", false)) return when (intent.getStringExtra("op")) {
            "create" -> str(R.string.ps3_online_created)
            "sign_out" -> str(R.string.ps3_online_signed_out)
            else -> if (npid.isNotEmpty()) str(R.string.ps3_online_signed_in, npid) else str(R.string.ps3_online_not_signed_in)
        }
        return when (intent.getStringExtra("error")) {
            "taken" -> str(R.string.ps3_online_taken)
            "bad_login" -> str(R.string.ps3_online_bad_login)
            "bad_token" -> str(R.string.ps3_online_bad_token)
            "unreachable" -> str(R.string.ps3_online_unreachable)
            "invalid" -> str(R.string.ps3_online_invalid)
            else -> str(R.string.ps3_online_failed, intent.getStringExtra("message").orEmpty())
        }
    }
}

/** Settings → PS3 Online: sign in to RPCN, or make an account, for games' own online modes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Ps3OnlineCard() {
    val context = LocalContext.current
    if (!remember { Ps3Online.installed(context) }) return
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                signedIn = intent.getStringExtra("npid").orEmpty()
                if (signedIn.isNotEmpty()) password = ""  // the player has it now; not kept on screen
                if (signedIn.isNotEmpty() && name.isEmpty()) name = signedIn
                if (intent.getStringExtra("op") != "status" || !intent.getBooleanExtra("ok", false)) status = Ps3Online.describe(intent)
                if (intent.getStringExtra("op") == "create" && intent.getBooleanExtra("ok", false)) creating = false
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Ps3Online.STATUS), ContextCompat.RECEIVER_EXPORTED)
        runCatching { Ps3Online.send(context, "status") }
        onDispose { context.unregisterReceiver(receiver) }
    }
    fun send(op: String, vararg extras: Pair<String, String>) {
        status = str(R.string.ps3_online_working)
        runCatching { Ps3Online.send(context, op, *extras) }.onFailure { status = str(R.string.ps3_online_failed, it.message.orEmpty()) }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.ps3_online_title))
        CardBody(stringResource(R.string.ps3_online_body))
        CardBody(if (signedIn.isNotEmpty()) stringResource(R.string.ps3_online_signed_in, signedIn) else stringResource(R.string.ps3_online_not_signed_in))
        if (signedIn.isEmpty()) {
            ControllerTextField(value = name, onValueChange = { name = it.take(16) }, label = { Text(stringResource(R.string.ps3_online_name)) }, modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = password, onValueChange = { password = it.take(64) }, label = { Text(stringResource(R.string.ps3_online_password)) },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (creating) {
                ControllerTextField(value = email, onValueChange = { email = it.take(100) }, label = { Text(stringResource(R.string.ps3_online_email)) }, modifier = Modifier.fillMaxWidth())
                CardBody(stringResource(R.string.ps3_online_create_body))
            } else {
                ControllerTextField(value = token, onValueChange = { token = it.trim().take(32) }, label = { Text(stringResource(R.string.ps3_online_token)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                signedIn.isNotEmpty() -> FocusChip(stringResource(R.string.ps3_online_sign_out), onClick = { send("sign_out") })
                creating -> {
                    FocusChip(stringResource(R.string.ps3_online_create), enabled = name.isNotBlank() && password.isNotBlank() && email.isNotBlank(), onClick = {
                        send("create", "npid" to name.trim(), "password" to password, "email" to email.trim())
                    })
                    FocusChip(stringResource(R.string.ps3_online_have_account), onClick = { creating = false })
                }
                else -> {
                    FocusChip(stringResource(R.string.ps3_online_sign_in), enabled = name.isNotBlank() && password.isNotBlank(), onClick = {
                        send("sign_in", "npid" to name.trim(), "password" to password, "token" to token)
                    })
                    FocusChip(stringResource(R.string.ps3_online_new_account), onClick = { creating = true })
                }
            }
        }
        status?.let { CardBody(it) }
    }
}

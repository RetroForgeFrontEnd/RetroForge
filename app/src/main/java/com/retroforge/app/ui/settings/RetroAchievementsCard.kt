package com.retroforge.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.retroforge.app.R
import com.retroforge.app.player.RaAccount
import com.retroforge.app.player.RaEvent
import com.retroforge.app.player.RaSession
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone

/**
 * RetroAchievements: sign in with a retroachievements.org account (the password goes to RetroAchievements only;
 * RetroForge keeps the sign-in token it sends back), achievements on or off, and hardcore.
 */
@Composable
fun RetroAchievementsCard() {
    val context = LocalContext.current
    val account = remember { RaAccount(context) }
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(account.signedIn) }
    var enabled by remember { mutableStateOf(account.enabled) }
    var hardcore by remember { mutableStateOf(account.hardcore) }
    var user by remember { mutableStateOf(account.user) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<RaSession?>(null) }
    DisposableEffect(Unit) { onDispose { session?.stop() } }

    fun signIn() {
        busy = true
        status = null
        val s = RaSession(scope) { event ->
            when (event) {
                is RaEvent.SignedIn -> {
                    account.signedIn(event.user, event.token, event.displayName, event.points)
                    signedIn = true
                    busy = false
                    password = ""
                    session?.stop()
                    session = null
                }
                is RaEvent.SignInFailed -> {
                    status = event.reason
                    busy = false
                    session?.stop()
                    session = null
                }
                else -> Unit
            }
        }
        session = s
        s.start(hardcore = false)
        s.signIn(user, password = password)
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("RetroAchievements")
        if (signedIn) StatusPill(stringResource(R.string.ra_signed_in_as, account.displayName, account.points), StatusTone.GOOD)
        else StatusPill(stringResource(R.string.ra_not_signed_in), StatusTone.NEUTRAL)
        CardBody(stringResource(R.string.ra_card_body))
        if (signedIn) {
            ChipRow {
                FocusChip(stringResource(if (enabled) R.string.ra_achievements_on else R.string.ra_achievements_off), onClick = {
                    enabled = !enabled
                    account.enabled = enabled
                })
                FocusChip(stringResource(if (hardcore) R.string.ra_hardcore_on else R.string.ra_hardcore_off), onClick = {
                    hardcore = !hardcore
                    account.hardcore = hardcore
                })
                FocusChip(stringResource(R.string.settings_sign_out), onClick = {
                    account.signOut()
                    signedIn = false
                })
            }
            CardBody(stringResource(R.string.ra_hardcore_body))
        } else {
            ControllerTextField(value = user, onValueChange = { user = it.trim() }, singleLine = true,
                label = { Text(stringResource(R.string.ra_username)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            ControllerTextField(
                value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(R.string.settings_password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            status?.let { CardBody(it) }
            ChipRow {
                FocusChip(if (showPassword) stringResource(R.string.network_drives_hide_password) else stringResource(R.string.network_drives_show_password), onClick = { showPassword = !showPassword })
                FocusChip(stringResource(if (busy) R.string.ra_signing_in else R.string.ra_sign_in), onClick = ::signIn, enabled = !busy && user.isNotBlank() && password.isNotEmpty())
                FocusChip(stringResource(R.string.settings_create_a_free_account), onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://retroachievements.org/createaccount".toUri())) }
                })
            }
        }
    }
}

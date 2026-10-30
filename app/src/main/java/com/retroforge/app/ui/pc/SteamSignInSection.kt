package com.retroforge.app.ui.pc

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.pcplayer.steam.SteamAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Signing in to Steam for RetroForge's own PC player, so Steam games you own can start. On a phone: account name +
 * password with Steam Guard (approve in the Steam app, or type its code or the emailed one). With the Steam app on
 * another device: a QR code. (Opening the QR code's link in the Steam app on the same phone doesn't work: the app
 * refuses links that weren't scanned, "for security purposes".)
 */
/**
 * A sign-in in progress lives here, not in the card: the card is thrown away when it scrolls off screen or the user
 * switches to the Steam app to approve, and the sign-in has to keep going meanwhile.
 */
private object SignIn {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var job by mutableStateOf<Job?>(null)
    var message by mutableStateOf<String?>(null)
    var qr by mutableStateOf<Bitmap?>(null)
    // A Steam Guard code Steam is waiting for (email address, or null for the Steam app's code).
    var codeFor by mutableStateOf<Pair<String?, CompletableDeferred<String>>?>(null)
    var signedInAs by mutableStateOf<String?>(null)
}

@Composable
fun SteamSignInSection() {
    val context = LocalContext.current
    val account = remember { SteamAccount(context.applicationContext) }
    var signedInAs by SignIn::signedInAs
    var job by SignIn::job
    var message by SignIn::message
    var qr by SignIn::qr
    var codeFor by SignIn::codeFor
    if (signedInAs == null && account.signedIn) signedInAs = account.accountName
    var askPassword by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val prompts = object : SteamAccount.SignInPrompts {
        override suspend fun code(email: String?, previousWasWrong: Boolean): String {
            val answer = CompletableDeferred<String>()
            message = when {
                previousWasWrong -> "That code wasn't right. Try the newest one."
                email != null -> "Steam emailed a code to $email."
                else -> "Type the code from the Steam app (Steam Guard)."
            }
            code = ""
            codeFor = email to answer
            return answer.await().also { codeFor = null }
        }

        override fun approveInSteamApp() {
            message = "Open the Steam app and approve the sign-in (it may show as a notification)."
        }

        override fun qrLink(url: String) {
            qr = qrBitmap(url)
        }
    }

    fun run(block: suspend () -> String) {
        job?.cancel()
        message = "Connecting to Steam…"
        job = SignIn.scope.launch {
            runCatching { block() }
                .onSuccess { signedInAs = it; message = null; password = "" }
                .onFailure {
                    android.util.Log.w("PcSteam", "Sign-in failed", it)
                    // A real cancel (the Cancel button) clears the job first; any other stop is an error to show.
                    if (job != null) message = "Couldn't sign in: ${it.message ?: it.javaClass.simpleName}"
                }
            qr = null
            codeFor = null
            job = null
        }
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Steam (for RetroForge's PC player)")
        if (signedInAs != null) {
            CardBody("Signed in as $signedInAs. Steam games you own start in RetroForge's PC player with your account's ticket from Steam.")
            ChipRow {
                FocusChip("Sign out", onClick = { account.signOut(); signedInAs = null })
            }
            return@FocusCard
        }
        CardBody(
            "Steam games check that Steam is running and that you own them. Sign in so RetroForge can get each game's " +
                "ticket from Steam, as GameNative does. Your password goes to Steam only; RetroForge keeps just the " +
                "sign-in token Steam gives it.",
        )
        ChipRow {
            FocusChip("Sign in with password", enabled = job == null, onClick = { askPassword = !askPassword })
            FocusChip("Show a QR code", enabled = job == null, onClick = {
                run {
                    account.signInWithQr(object : SteamAccount.SignInPrompts by prompts {
                        override fun qrLink(url: String) {
                            prompts.qrLink(url)
                            message = "Scan this with the Steam app on another device (Steam Guard → Scan a QR code)."
                        }

                        // A QR sign-in is approved by scanning; keep the scan instructions on screen.
                        override fun approveInSteamApp() {}
                    })
                }
            })
            if (job != null) FocusChip("Cancel", onClick = { job?.cancel(); job = null; message = null; qr = null; codeFor = null })
        }
        if (askPassword && job == null) {
            ControllerTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Steam account name") }, modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            FocusChip("Sign in", enabled = name.isNotBlank() && password.isNotEmpty(), onClick = {
                val n = name
                val p = password
                run { account.signInWithPassword(n, p, prompts) }
            })
        }
        codeFor?.let { (_, answer) ->
            ControllerTextField(value = code, onValueChange = { code = it.trim().uppercase() }, singleLine = true,
                label = { Text("Steam Guard code") }, modifier = Modifier.fillMaxWidth())
            FocusChip("Send code", enabled = code.length >= 5, onClick = { answer.complete(code) })
        }
        if (job != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { CardBody(it) }
        qr?.let { Image(it.asImageBitmap(), contentDescription = "Steam sign-in QR code", modifier = Modifier.size(220.dp)) }
    }
}

private fun qrBitmap(text: String): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

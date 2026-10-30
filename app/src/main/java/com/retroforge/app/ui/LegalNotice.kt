package com.retroforge.app.ui

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.Wordmark

/** Bump when the notice's meaning changes, so everyone sees the new version once. */
const val LEGAL_NOTICE_VERSION = 1

val legalNoticeParagraphs = listOf(
    "RetroForge does not include, sell or download any games (ROMs or disc images), BIOS or firmware files.",
    "To play, you need your own copies, obtained legally: for example, made from cartridges, discs or consoles you own. " +
        "The rules differ from country to country, and you are responsible for the files you use.",
    "RetroForge only reads the folders you choose. Emulators such as RetroArch are separate apps from their own developers.",
    "Nintendo, Sony, Sega and other names are trademarks of their owners. RetroForge is not affiliated with or endorsed by them.",
)

/** Shown once at first launch (and again if the notice changes). The app can't be used until it's acknowledged. */
@Composable
fun LegalNoticeScreen(onAcknowledge: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ScreenFrame(title = null) {
        Wordmark()
        Text(stringResource(R.string.legal_notice_before_you_start), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        legalNoticeParagraphs.forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FocusCard(modifier = Modifier.fillMaxWidth().focusRequester(focus), onClick = onAcknowledge) {
            CardTitle(stringResource(R.string.legal_notice_i_understand))
            CardBody(stringResource(R.string.legal_notice_you_can_read_this_again_any))
        }
    }
}

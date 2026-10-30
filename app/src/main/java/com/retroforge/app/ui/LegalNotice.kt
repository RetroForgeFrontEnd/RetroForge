package com.retroforge.app.ui

import com.retroforge.core.ui.str
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

val legalNoticeParagraphs get() = listOf(
    str(R.string.legal_notice_retroforge_does_not_include_sell_or),
    str(R.string.legal_notice_to_play_you_need_your_own),
    str(R.string.legal_notice_retroforge_only_reads_the_folders_you),
    str(R.string.legal_notice_nintendo_sony_sega_and_other_names),
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

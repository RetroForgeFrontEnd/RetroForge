package com.retroforge.app.ui.settings

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip

/** Donation links, only those set for this build. Every feature stays free; supporting is optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportCard() {
    val links = listOf(
        "Buy Me a Coffee" to BuildConfig.SUPPORT_BUYMEACOFFEE,
        "Ko-fi" to BuildConfig.SUPPORT_KOFI,
        "Patreon" to BuildConfig.SUPPORT_PATREON,
        "GitHub Sponsors" to BuildConfig.SUPPORT_GITHUB,
    ).filter { it.second.startsWith("https://") }
    if (links.isEmpty()) return
    val context = LocalContext.current
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.support_support_retroforge))
        CardBody(
            stringResource(R.string.support_retroforge_is_free_with_every_feature),
        )
        // On a TV box or handheld, scan this with your phone.
        if (BuildConfig.SUPPORT_BUYMEACOFFEE.startsWith("https://")) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.retroforge.app.R.drawable.support_qr),
                contentDescription = "QR code for Buy Me a Coffee",
                modifier = Modifier.size(180.dp),
            )
            CardBody(stringResource(R.string.support_scan_with_your_phone_or_use))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { (name, url) -> FocusChip(name, onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }) }
    }
}

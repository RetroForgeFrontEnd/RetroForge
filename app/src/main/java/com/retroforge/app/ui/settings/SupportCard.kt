package com.retroforge.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
        CardTitle("Support RetroForge")
        CardBody("RetroForge is free, with every feature included. If it's made your games better, you can help fund its development.")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { (name, url) -> FocusChip(name, onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }) }
    }
}

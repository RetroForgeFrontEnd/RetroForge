package com.retroforge.app.ui.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard

data class UsefulLink(val title: String, val url: String, val summary: String)

data class LinkGroup(val title: String, val links: List<UsefulLink>)

/**
 * Hand-picked channels, each checked against its own YouTube page on 2026-10-28 (handle resolves to the named
 * channel). Summaries are our own wording. Opening one hands the address to the YouTube app or browser.
 */
val usefulLinks = listOf(
    LinkGroup(
        "YouTube: handhelds and emulation",
        listOf(
            UsefulLink("Retro Game Corps", "https://www.youtube.com/@RetroGameCorps", "Russ's in-depth handheld reviews and emulation setup guides."),
            UsefulLink("ETA PRIME", "https://www.youtube.com/@ETAPRIME", "Emulation and gaming on Android, PCs, Raspberry Pi and single-board computers."),
            UsefulLink("TechDweeb", "https://www.youtube.com/@TechDweeb", "Retro handheld reviews and comparisons, from budget emulation devices to the latest Android handhelds."),
            UsefulLink("Ryan Retro","https://www.youtube.com/@RyanRetro", "Easy-to-follow handheld gaming guides for beginners and experienced players."),
            UsefulLink("Mr. Sujano", "https://www.youtube.com/@MrSujano", "Gaming and tech news, with handhelds, controllers and consoles."),
            UsefulLink("Wicked Gamer & Collector", "https://www.youtube.com/@wickedgamercollector", "Retro hardware, games, emulation and arcade."),
            UsefulLink("Modern Vintage Gamer", "https://www.youtube.com/@ModernVintageGamer", "How classic games and consoles work, homebrew and emulation history."),
            UsefulLink("Digital Foundry", "https://www.youtube.com/@DigitalFoundry", "In-depth technical analysis, including retro games, remasters and emulation."),
            UsefulLink("My Life in Gaming", "https://www.youtube.com/@MyLifeinGaming", "Retro gaming hardware, best ways to play old games, and RGB video guides."),
            UsefulLink("The Gaming Historian", "https://www.youtube.com/@GamingHistorian", "Well-researched documentaries on video game history."),
            UsefulLink("Wicked Package From China", "https://www.youtube.com/@PackageFromChina", "Gadgets and handhelds from AliExpress, Amazon and other Chinese stores."),
        ),
    ),
)

@Composable
fun LinksScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    ScreenFrame(title = "Retro games, handhelds and PC gaming", subtitle = "Channels and sites worth following. Links open in YouTube or your browser.") {
        usefulLinks.forEach { group ->
            Text(group.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            // A to Z, ignoring a leading "The" (The Gaming Historian goes under G).
            group.links.sortedBy { it.title.removePrefix("The ").lowercase() }.forEach { link ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { error = open(context, link.url) }) {
                    CardTitle(link.title)
                    CardBody(link.summary)
                    Text(link.url.removePrefix("https://www."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        error?.let { CardBody(it) }
    }
}

private fun open(context: Context, url: String): String? = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (_: ActivityNotFoundException) {
    "No app on this device can open links. Visit $url on another device."
}

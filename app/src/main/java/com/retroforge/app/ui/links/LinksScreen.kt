package com.retroforge.app.ui.links

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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
val usefulLinks get() = listOf(
    LinkGroup(
        str(R.string.links_youtube_handhelds_and_emulation),
        listOf(
            UsefulLink("Retro Game Corps", "https://www.youtube.com/@RetroGameCorps", str(R.string.links_russ_s_in_depth_handheld_reviews)),
            UsefulLink("ETA PRIME", "https://www.youtube.com/@ETAPRIME", str(R.string.links_emulation_and_gaming_on_android_pcs)),
            UsefulLink("TechDweeb", "https://www.youtube.com/@TechDweeb", str(R.string.links_retro_handheld_reviews_and_comparisons_from)),
            UsefulLink("Ryan Retro","https://www.youtube.com/@RyanRetro", str(R.string.links_easy_to_follow_handheld_gaming_guides)),
            UsefulLink("Joey's Retro Handhelds", "https://www.youtube.com/@JoeysRetroHandhelds", str(R.string.links_retro_handheld_reviews_guides_and_joey)),
            UsefulLink("Taki Udon", "https://www.youtube.com/@TakiUdon", str(R.string.links_handheld_mods_and_fpga_hardware_from)),
            UsefulLink("Retro Handhelds", "https://www.youtube.com/RetroHandhelds", str(R.string.links_news_reviews_and_guides_for_retro)),
            UsefulLink("Mr. Sujano", "https://www.youtube.com/@MrSujano", str(R.string.links_gaming_and_tech_news_with_handhelds)),
            UsefulLink("Wicked Gamer & Collector", "https://www.youtube.com/@wickedgamercollector", str(R.string.links_retro_hardware_games_emulation_and_arcade)),
            UsefulLink("Modern Vintage Gamer", "https://www.youtube.com/@ModernVintageGamer", str(R.string.links_how_classic_games_and_consoles_work)),
            UsefulLink("Digital Foundry", "https://www.youtube.com/@DigitalFoundry", str(R.string.links_in_depth_technical_analysis_including_retro)),
            UsefulLink("My Life in Gaming", "https://www.youtube.com/@MyLifeinGaming", str(R.string.links_retro_gaming_hardware_best_ways_to)),
            UsefulLink("The Gaming Historian", "https://www.youtube.com/@GamingHistorian", str(R.string.links_well_researched_documentaries_on_video_game)),
            UsefulLink("Wicked Package From China", "https://www.youtube.com/@PackageFromChina", str(R.string.links_gadgets_and_handhelds_from_aliexpress_amazon)),
        ),
    ),
)

@Composable
fun LinksScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    ScreenFrame(title = stringResource(R.string.links_retro_games_handhelds_and_pc_gaming), subtitle = stringResource(R.string.links_channels_and_sites_worth_following_links)) {
        NewsSection(onOpen = { error = open(context, it) })
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
    str(R.string.links_no_app_on_this_device_can, url)
}

package com.retroforge.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.Wordmark

@Composable
fun AboutScreen() {
    ScreenFrame(title = null) {
        Wordmark()
        CardBody("Version ${BuildConfig.VERSION_NAME}")

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("RetroForge stands for…")
            CardBody("…sorry, wrong YouTube channel. (A nod to Mr Sujano. Go and watch him.)")
            CardBody(
                "RetroForge doesn't stand for anything. It's what it does: it forges all of retro gaming into one place, " +
                    "so you set it up once and then just play.",
            )
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("What it does")
            CardBody("• Finds the games you already have, on your phone or SD card, and shows them with box art, by system.")
            CardBody("• Plays them in RetroArch with the right core and BIOS checks. PC games go through a separately installed PC game runtime.")
            CardBody("• Patches ROM hacks and translations, and helps you discover more from catalogues you choose.")
            CardBody("• Tidies your collection: removes duplicates, compresses discs to CHD and cartridges to .zip, and makes multi-disc playlists. Every change is shown first and can be undone.")
            CardBody("• Works with a controller from the sofa as well as with touch.")
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("One app instead of ten")
            CardBody(
                "Retro gaming on Android usually means a frontend, an emulator per system, a scraper, a patcher, a file manager and a pile of " +
                    "forum threads. RetroForge's aim is to bring all of that together: easy first setup, sensible defaults, and the power " +
                    "tools there when you want them.",
            )
            CardBody(
                "It works alongside what you already have. Your folders stay where they are, other frontends keep working, " +
                    "and nothing changes on disk unless you confirm it.",
            )
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Your games, your BIOS")
            CardBody("RetroForge comes with no games and no BIOS files. Use your own copies of games and BIOS you own.")
        }
    }
}

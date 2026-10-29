package com.retroforge.app.ui

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.compose.AsyncImage
import com.retroforge.app.R

/**
 * The second display of a dual-screen device (AYN Thor, AYANEO Pocket DS …) while browsing: RetroForge's key art,
 * or the game whose page is open (cover, title, system and description), like a game box on the shelf.
 */
object SecondScreen {
    data class Shown(val title: String, val system: String, val description: String?, val art: Any?)

    /** What the second screen shows; null for the key art. Set by the game page. */
    var shown by mutableStateOf<Shown?>(null)

    private var presentation: Presentation? = null

    fun show(activity: ComponentActivity, enabled: Boolean) {
        if (!enabled || presentation != null) return
        val display = activity.getSystemService(DisplayManager::class.java)
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull() ?: return
        presentation = object : Presentation(activity, display) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(ComposeView(context).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setContent { Content() }
                })
            }
        }.also { p ->
            p.setOnDismissListener { presentation = null }
            runCatching { p.show() }.onFailure { presentation = null }
        }
    }

    fun hide() {
        presentation?.dismiss()
        presentation = null
    }

    fun hasSecondScreen(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()

    @Composable
    private fun Content() {
        val game = shown
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B14))) {
            if (game == null) {
                Image(painterResource(R.drawable.key_art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (game.art != null) {
                        AsyncImage(model = game.art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxHeight().weight(1f))
                    }
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(game.system.uppercase(), color = Color(0xFFFF8A1F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(game.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        game.description?.let { Text(it, color = Color(0xFFC8C8D2), fontSize = 15.sp, maxLines = 12, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

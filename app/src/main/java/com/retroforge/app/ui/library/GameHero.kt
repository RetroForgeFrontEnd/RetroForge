package com.retroforge.app.ui.library

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.retroforge.core.library.GameEntity
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.LocalThemeSpec

/**
 * A game shown big: its own cover blurred across the background, the sharp box art and title on top, and an
 * optional action. Used for "Jump back in" on Home and at the top of a game's page.
 */
@Composable
fun GameHero(
    game: GameEntity,
    image: Any?,
    eyebrow: String?,
    subtitle: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bigArt: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().heightIn(min = 230.dp).clip(MaterialTheme.shapes.large)) {
        // Backdrop: the cover, blurred (Android 12+) or heavily dimmed, else the theme's accent colours.
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(30.dp) else Modifier.alpha(0.45f)),
            )
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.55f), colors.secondary.copy(alpha = 0.35f)))))
        }
        // A scrim so the title always reads, whatever the cover looks like.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.15f), colors.background.copy(alpha = 0.85f))),
            ),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 480.dp
            val bigWidth = minOf(maxWidth * 0.62f, 300.dp)
            val details: @Composable () -> Unit = {
                eyebrow?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary) }
                Text(game.title, style = MaterialTheme.typography.headlineSmall, color = colors.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2) }
                if (actionLabel != null && onAction != null) Box(Modifier.padding(top = 6.dp)) { FocusChip(actionLabel, onClick = onAction) }
            }
            if (bigArt && narrow) {
                // A game's own page on a phone: the cover large and centred, the title under it.
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GameArt(game, image, Modifier.width(bigWidth).aspectRatio(LocalThemeSpec.current.tileAspect.ratio))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { details() }
                }
            } else {
                val artWidth = when {
                    bigArt -> 260.dp
                    narrow -> 120.dp
                    else -> 170.dp
                }
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                    GameArt(game, image, Modifier.width(artWidth).aspectRatio(LocalThemeSpec.current.tileAspect.ratio))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { details() }
                }
            }
        }
    }
}

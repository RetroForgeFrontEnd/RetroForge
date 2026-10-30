package com.retroforge.app.ui.library

import com.retroforge.core.ui.LocalThemeSpec
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.retroforge.core.library.GameEntity
import com.retroforge.core.ui.FocusCard

/**
 * A game's artwork, or its title on a plain tile when there is none (or it fails to load).
 * [image] is a chosen cover file, a content:// URI of the gamelist artwork, or null.
 */
@Composable
fun GameArt(game: GameEntity, image: Any?, modifier: Modifier = Modifier, fill: Boolean = false) {
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                game.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Box(modifier.clip(MaterialTheme.shapes.small)) {
        if (image == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = image,
                contentDescription = null,
                contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun GameTile(game: GameEntity, image: Any?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(modifier = modifier, onClick = onClick) {
        GameArt(game, image, Modifier.fillMaxWidth().aspectRatio(LocalThemeSpec.current.tileAspect.ratio))
        Text(
            game.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        tagLine(game)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One game as a row (themes with a list layout): small art beside the title. */
@Composable
fun GameRow(game: GameEntity, image: Any?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(modifier = modifier, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GameArt(game, image, Modifier.width(72.dp).aspectRatio(LocalThemeSpec.current.tileAspect.ratio))
            Column(Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                tagLine(game)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

fun tagLine(game: GameEntity): String? = listOfNotNull(
    game.regions.ifEmpty { null },
    game.discs?.let { if (it == 1) "1 disc" else "$it discs" },
    if (game.isHack) game.hackFolder ?: "Hack" else null,
    if (game.duplicateKey != null) "Also another copy" else null,
).joinToString(" · ").ifEmpty { null }

/**
 * One shelf of the bookshelf view (Retro tab → Games: bookshelf): game boxes standing side by side on a glass shelf,
 * like a console dashboard's game library. The chosen box lifts a little; [columns] keeps a short last shelf in step.
 */
@Composable
fun GameShelf(games: List<Triple<GameEntity, Any?, java.io.File?>>, columns: Int, onOpen: (GameEntity) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            games.forEach { (game, image, box3d) -> ShelfBox(game, image, box3d, { onOpen(game) }, Modifier.weight(1f)) }
            repeat(columns - games.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
        }
        val edge = MaterialTheme.colorScheme.primary
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(edge.copy(alpha = 0.55f), MaterialTheme.colorScheme.surfaceVariant))),
        )
        Box(Modifier.fillMaxWidth().height(14.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0x88000000), androidx.compose.ui.graphics.Color.Transparent))))
    }
}

@Composable
private fun ShelfBox(game: GameEntity, image: Any?, box3d: java.io.File?, onClick: () -> Unit, modifier: Modifier) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val lift by androidx.compose.animation.core.animateFloatAsState(if (focused || pressed) 1f else 0f, label = "lift")
    Column(
        modifier
            .graphicsLayer { scaleX = 1f + 0.08f * lift; scaleY = 1f + 0.08f * lift; translationY = -10.dp.toPx() * lift }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A 3D box render (ScreenScraper) already is a case: shown as it is, standing on the shelf.
        if (box3d != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.78f), contentAlignment = Alignment.BottomCenter) {
                coil3.compose.AsyncImage(
                    model = box3d,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier),
                )
            }
        } else ShelfCase(game, image, focused, lift)
        Text(
            game.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
    }
}

/** A game case in 3D, drawn round the flat cover: turned a little to show its spine, square on when chosen. */
@Composable
private fun ShelfCase(game: GameEntity, image: Any?, focused: Boolean, lift: Float) {
    val turn = 22f * (1f - lift)
    Row(
        Modifier.fillMaxWidth().aspectRatio(0.78f)
            .graphicsLayer { rotationY = turn; cameraDistance = 14f * density }
            .shadow(if (focused) 14.dp else 6.dp, RoundedCornerShape(3.dp)),
    ) {
        // The spine: the case's own edge, darker, catching less light.
        Box(
            Modifier.fillMaxHeight().weight(0.09f)
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF15151B), androidx.compose.ui.graphics.Color(0xFF3A3A46)))),
        )
        Box(
            Modifier.fillMaxHeight().weight(0.91f).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF101014))
                .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)) else Modifier),
        ) {
            GameArt(game, image, Modifier.fillMaxSize(), fill = true)  // a printed sleeve: the cover fills the front
            // The plastic sleeve's shine.
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        0f to androidx.compose.ui.graphics.Color(0x40FFFFFF), 0.35f to androidx.compose.ui.graphics.Color.Transparent,
                        0.8f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color(0x22FFFFFF),
                    ),
                ),
            )
        }
    }
}

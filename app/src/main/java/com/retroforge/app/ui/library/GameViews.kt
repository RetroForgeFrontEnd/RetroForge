package com.retroforge.app.ui.library

import android.net.Uri
import androidx.compose.foundation.background
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

/** Artwork from the user's own gamelist media, or the title on a plain tile when there is none (or it fails). */
@Composable
fun GameArt(game: GameEntity, image: Uri?, modifier: Modifier = Modifier) {
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
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        if (image == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun GameTile(game: GameEntity, image: Uri?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(modifier = modifier, onClick = onClick) {
        GameArt(game, image, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
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

fun tagLine(game: GameEntity): String? = listOfNotNull(
    game.regions.ifEmpty { null },
    game.discs?.let { "$it discs" },
    if (game.isHack) game.hackFolder ?: "Hack" else null,
    if (game.duplicateKey != null) "Also another copy" else null,
).joinToString(" · ").ifEmpty { null }

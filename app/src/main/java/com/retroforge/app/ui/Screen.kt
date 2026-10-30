package com.retroforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.retroforge.core.ui.LocalCompactCards
import com.retroforge.core.ui.ThemeBackdrop

/**
 * The red ✕ in the corner normally leaves the page. A page with steps of its own (Settings' groups) takes it over
 * while it's deeper than its first step, through [CloseStep].
 */
val LocalCloseOverride = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<(() -> Unit)?>> {
    androidx.compose.runtime.mutableStateOf(null)
}

/** While [enabled], Android's back and (when [takeOverClose]) the red ✕ run [onBack] instead of leaving the page. */
@Composable
fun CloseStep(enabled: Boolean, takeOverClose: Boolean = true, onBack: () -> Unit) {
    val holder = LocalCloseOverride.current
    val latest = androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.activity.compose.BackHandler(enabled) { latest.value() }
    androidx.compose.runtime.DisposableEffect(enabled, takeOverClose, holder) {
        val action: () -> Unit = { latest.value() }
        if (enabled && takeOverClose) holder.value = action
        onDispose { if (holder.value === action) holder.value = null }
    }
}

/**
 * Common scrolling page frame so every section has the same title treatment and spacing. [compact] (Settings) packs
 * the cards closer and makes them smaller (see [LocalCompactCards]).
 */
@Composable
fun ScreenFrame(title: String?, subtitle: String? = null, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // NavHost hands destinations fixed-width constraints; the Box relaxes them so the cap applies.
    ThemeBackdrop {
        CompositionLocalProvider(LocalCompactCards provides compact) {
            Column(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
            ) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                content()
            }
        }
    }
}

/**
 * A card's title. [status] (an On/Off pill and the like) goes at the end of the title line on compact pages, so it
 * doesn't take a line of its own, and under the title elsewhere.
 */
@Composable
fun CardTitle(text: String, status: (@Composable () -> Unit)? = null) {
    val compact = LocalCompactCards.current
    val style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    if (status != null && compact) {
        // A short pill (On, Off …) sits at the end of the title line; a long one would squeeze the title, so it goes under.
        androidx.compose.ui.layout.Layout(content = {
            Text(text, style = style, color = MaterialTheme.colorScheme.onSurface)
            status()
        }) { (title, pill), constraints ->
            val gap = 12.dp.roundToPx()
            val free = constraints.copy(minWidth = 0, minHeight = 0)
            val pillPlaced = pill.measure(free)
            if (pillPlaced.width <= constraints.maxWidth / 3) {
                val titlePlaced = title.measure(free.copy(maxWidth = (constraints.maxWidth - pillPlaced.width - gap).coerceAtLeast(0)))
                val height = maxOf(titlePlaced.height, pillPlaced.height)
                layout(constraints.maxWidth, height) {
                    titlePlaced.place(0, (height - titlePlaced.height) / 2)
                    pillPlaced.place(constraints.maxWidth - pillPlaced.width, (height - pillPlaced.height) / 2)
                }
            } else {
                val titlePlaced = title.measure(free)
                val spacing = 8.dp.roundToPx()
                layout(constraints.maxWidth, titlePlaced.height + spacing + pillPlaced.height) {
                    titlePlaced.place(0, 0)
                    pillPlaced.place(0, titlePlaced.height + spacing)
                }
            }
        }
        return
    }
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurface)
    status?.invoke()
}

/** A card's explanation. On compact pages it's cut to two lines, with More (and then Less) when there's more to read. */
@Composable
fun CardBody(text: String) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (!LocalCompactCards.current) {
        Text(text, style = style, color = color)
        return
    }
    var expanded by remember(text) { mutableStateOf(false) }
    var cut by remember(text) { mutableStateOf(false) }
    Text(
        text, style = style, color = color,
        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
    )
    if (cut || expanded) {
        Text(
            if (expanded) "Less" else "More",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
        )
    }
}

/** A row of buttons that wraps onto the next line on narrow screens instead of squashing the last one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/**
 * How far a download or scan has got: "42%" and a bar, or a moving bar while it isn't known yet ([progress] null or 0).
 */
@Composable
fun DownloadBar(progress: Float?) {
    if (progress != null && progress > 0f) {
        CardBody("${(progress * 100).toInt()}%")
        androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

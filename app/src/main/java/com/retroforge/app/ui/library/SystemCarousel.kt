package com.retroforge.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retroforge.app.ui.CardBody
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.LocalThemeSpec
import com.retroforge.theme.SystemView

/** Systems as the theme wants them: a swipeable row of big cards (carousel) or a grid of cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemsView(systems: List<SystemSummary>, onOpenSystem: (String) -> Unit) {
    if (LocalThemeSpec.current.systemView == SystemView.CAROUSEL) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(systems, key = { it.id }) { s -> SystemCard(s, Modifier.width(260.dp).height(170.dp), big = true) { onOpenSystem(s.id) } }
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            systems.forEach { s -> SystemCard(s, Modifier.width(220.dp).heightIn(min = 110.dp), big = false) { onOpenSystem(s.id) } }
        }
    }
}

@Composable
private fun SystemCard(system: SystemSummary, modifier: Modifier, big: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FocusCard(modifier = modifier, onClick = onClick) {
        Column {
            // A slim accent bar in the theme's colours gives each card some character without artwork.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall).background(Brush.horizontalGradient(listOf(colors.primary, colors.secondary))))
            Text(
                system.name,
                style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            CardBody(plural(system.games, "game") + if (system.hacks > 0) " · ${plural(system.hacks, "hack")}" else "")
        }
    }
}

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retroforge.core.ui.ThemeBackdrop

/** Common scrolling page frame so every section has the same title treatment and spacing. */
@Composable
fun ScreenFrame(title: String?, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    // NavHost hands destinations fixed-width constraints; the Box relaxes them so the cap applies.
    ThemeBackdrop {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

@Composable
fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun CardBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A row of buttons that wraps onto the next line on narrow screens instead of squashing the last one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

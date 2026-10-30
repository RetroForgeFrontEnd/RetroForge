package com.retroforge.app.ui.pc

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard

/** One section of the PC tab: a card with its title and a line about it, selected to show or hide what's in it. */
@Composable
fun PcFold(title: String, summary: String, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { open = !open }) {
        CardTitle((if (open) "▾ " else "▸ ") + title)
        if (!open) CardBody(summary)
    }
    if (open) content()
}

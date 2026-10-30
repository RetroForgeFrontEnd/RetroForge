package com.retroforge.app.ui.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.AppLanguage
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip

/** Settings → Looks and sounds: RetroForge's language, the phone's own or one picked here. */
@Composable
fun LanguageCard() {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(AppLanguage.current(context)) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.language_title))
        CardBody(stringResource(R.string.language_body))
        ChipRow {
            AppLanguage.choices.forEach { (tag, name) ->
                val label = name ?: stringResource(R.string.language_phone)
                FocusChip(if (tag == chosen) "● $label" else label, onClick = {
                    chosen = tag
                    AppLanguage.set(context, tag)
                    // Android 13+ redraws RetroForge in the new language itself; older versions need the screen made again.
                    if (Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
                })
            }
        }
    }
}

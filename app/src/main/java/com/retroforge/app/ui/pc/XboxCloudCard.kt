package com.retroforge.app.ui.pc

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard

/** Opens [XboxCloudActivity]: Microsoft's xbox.com/play with Better xCloud. */
@Composable
fun XboxCloudCard() {
    val context = LocalContext.current
    FocusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { context.startActivity(Intent(context, XboxCloudActivity::class.java)) },
    ) {
        CardTitle("Xbox Cloud Gaming")
        CardBody(
            "Stream Xbox games from xbox.com/play, improved by Better xCloud. Needs Game Pass with cloud gaming; you sign " +
                "in on Microsoft's own page. Not made or endorsed by Microsoft.",
        )
    }
}

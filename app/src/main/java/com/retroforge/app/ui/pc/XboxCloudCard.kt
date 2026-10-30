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
            "Stream Xbox games from Microsoft's own site, xbox.com/play, improved by Better xCloud (redphx, MIT). " +
                "Needs a Game Pass plan that includes cloud gaming and a fast connection. You sign in on Microsoft's page " +
                "with your own account; RetroForge never sees your password. A controller works once the stream starts; " +
                "games that support touch show touch controls. Back asks before leaving.",
        )
        CardBody("Not made or endorsed by Microsoft or Xbox. Select this card to open it.")
    }
}

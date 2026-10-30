package com.retroforge.app.ui.pc

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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
        CardTitle(stringResource(R.string.xbox_cloud_xbox_cloud_gaming))
        CardBody(
            stringResource(R.string.xbox_cloud_stream_xbox_games_from_xbox_com),
        )
    }
}

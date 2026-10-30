package com.retroforge.app.ui.pc

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip

/** A cloud gaming service with its own Android app (opened, or its Play Store page when it isn't installed), or a web page. */
private class CloudService(val name: String, val pkg: String?, val web: String)

private val services = listOf(
    CloudService("GeForce NOW", "com.nvidia.geforcenow", "https://play.geforcenow.com"),
    CloudService("Boosteroid", "com.boosteroid.streaming", "https://boosteroid.com"),
    CloudService("Amazon Luna", null, "https://luna.amazon.com"), // plays in the browser on Android phones
)

/** Other cloud gaming services, each in its own official app or site. RetroForge only opens them. */
@Composable
fun CloudAppsCard() {
    val context = LocalContext.current
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ } // an app installed meanwhile changes Get to Open
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.cloud_apps_more_cloud_gaming))
        CardBody(
            stringResource(R.string.cloud_apps_geforce_now_and_boosteroid_stream_pc),
        )
        ChipRow {
            services.forEach { service ->
                val launch = remember(resumed) { service.pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } }
                val label = if (launch != null || service.pkg == null) "Open ${service.name}" else "Get ${service.name}"
                FocusChip(label, onClick = { open(context, service, launch) })
            }
        }
    }
}

private fun open(context: Context, service: CloudService, launch: Intent?) {
    if (launch != null) {
        context.startActivity(launch)
        return
    }
    val tries = listOfNotNull(
        service.pkg?.let { "market://details?id=$it" },
        service.pkg?.let { "https://play.google.com/store/apps/details?id=$it" },
        service.web,
    )
    tries.firstOrNull { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.isSuccess }
}

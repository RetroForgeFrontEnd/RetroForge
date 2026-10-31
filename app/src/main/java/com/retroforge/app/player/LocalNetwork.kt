package com.retroforge.app.player

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android 17's local network permission: without it the app can't reach other devices on the Wi-Fi, so netplay, link
 * cable, online rooms, network drives and PC streaming on the home network would all fail. Asked for once at start-up
 * and again right before any of those, if it was turned down.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val SINCE_SDK = 37

    /** True when the permission still has to be granted (always false before Android 17, which has no such thing). */
    fun needed(context: Context): Boolean =
        Build.VERSION.SDK_INT >= SINCE_SDK && context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED
}

/**
 * Runs the given work once the local network permission is granted, asking for it first when needed; [onDenied] runs
 * instead when the user turns it down.
 */
@Composable
fun rememberLocalNetwork(onDenied: () -> Unit = {}): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val denied by rememberUpdatedState(onDenied)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val then = pending[0]
        pending[0] = null
        if (granted) then?.invoke() else denied()
    }
    return remember(ask) {
        { work ->
            if (!LocalNetwork.needed(context)) work()
            else {
                pending[0] = work
                ask.launch(LocalNetwork.PERMISSION)
            }
        }
    }
}

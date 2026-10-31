package com.retroforge.app.ui.library

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.retroforge.app.R
import com.retroforge.app.player.Netplay
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.library.IntentExtra

/**
 * Online play for players that have multiplayer rooms: the RetroForge Switch Player joins or hosts one as the game
 * starts (its OnlineRoom), and games see the room as the Switch's local wireless play. The last address is remembered.
 */
object GameOnline {
    const val SWITCH_PLAYER = "RETROFORGE_SWITCH"
    const val PORT = 24872
    private const val PREFS = "online_rooms"
    private const val ADDRESS = "address"

    fun lastAddress(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ADDRESS, null).orEmpty()

    private fun remember(context: Context, address: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ADDRESS, address).apply()

    /** The launch extras that ask the player to host a room, or to join the one at [address] ("host" or "host:port"). */
    fun extras(context: Context, host: Boolean, address: String, password: String): List<IntentExtra> {
        val (where, port) = address.trim().let { a ->
            val p = a.substringAfterLast(':', "").toIntOrNull()
            if (p != null) a.substringBeforeLast(':') to p else a to PORT
        }
        if (!host) remember(context, address.trim())
        return listOfNotNull(
            IntentExtra.Text("room_mode", if (host) "host" else "join"),
            IntentExtra.Text("room_nickname", Netplay.name(context)),
            IntentExtra.Text("room_port", port.toString()),
            IntentExtra.Text("room_password", password),
            if (host) null else IntentExtra.Text("room_address", where),
        )
    }
}

/** The game page's online card: host a room, or join a friend's by address. [play] starts the game with the extras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameOnlineCard(play: (List<IntentExtra>) -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(GameOnline.lastAddress(context)) }
    var password by remember { mutableStateOf("") }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.online_title))
        CardBody(stringResource(R.string.online_body, Netplay.name(context), GameOnline.PORT))
        ControllerTextField(
            value = address, onValueChange = { address = it.take(80) }, singleLine = true,
            label = { Text(stringResource(R.string.online_address)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ControllerTextField(
            value = password, onValueChange = { password = it.take(40) }, singleLine = true,
            label = { Text(stringResource(R.string.online_password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip(stringResource(R.string.online_host), onClick = { play(GameOnline.extras(context, true, "", password)) })
            FocusChip(stringResource(R.string.online_join), enabled = address.isNotBlank(), onClick = {
                play(GameOnline.extras(context, false, address, password))
            })
        }
    }
}

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.retroforge.app.R
import com.retroforge.app.player.Netplay
import com.retroforge.app.player.NetplayHost
import com.retroforge.app.player.rememberLocalNetwork
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.library.GameEntity
import com.retroforge.library.IntentExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    fun extras(context: Context, game: GameEntity, host: Boolean, address: String, password: String): List<IntentExtra> {
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
            // Told to RetroForges on this Wi-Fi asking who's hosting, as with retro netplay.
            IntentExtra.Text("room_game_id", Netplay.gameId(game.system, game.fileName, 0)),
            IntentExtra.Text("room_title", game.title),
        )
    }
}

/**
 * The game page's online card: host a room, join one found on this Wi-Fi, or join a friend's by address.
 * [play] starts the game with the extras.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameOnlineCard(game: GameEntity, play: (List<IntentExtra>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(GameOnline.lastAddress(context)) }
    var password by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf<List<NetplayHost>?>(null) }
    var looking by remember { mutableStateOf(false) }
    val localNetwork = rememberLocalNetwork()
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.online_title))
        CardBody(stringResource(R.string.online_body, Netplay.name(context), GameOnline.PORT))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip(stringResource(R.string.online_host), onClick = { localNetwork { play(GameOnline.extras(context, game, true, "", password)) } })
            FocusChip(stringResource(if (looking) R.string.library_looking else R.string.library_find_games_on_this_wi_fi), enabled = !looking, onClick = {
                localNetwork {
                    looking = true
                    scope.launch {
                        rooms = withContext(Dispatchers.IO) { Netplay.find(context) }.filter { it.gameId.startsWith("${game.system}|") }
                        looking = false
                    }
                }
            })
        }
        rooms?.let { found ->
            if (found.isEmpty()) CardBody(stringResource(R.string.online_no_rooms))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                found.forEach { room ->
                    FocusChip(stringResource(R.string.online_join_room, room.player, room.title), onClick = {
                        localNetwork { play(GameOnline.extras(context, game, false, "${room.address}:${room.port}", password)) }
                    })
                }
            }
        }
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
        FocusChip(stringResource(R.string.online_join), enabled = address.isNotBlank(), onClick = {
            localNetwork { play(GameOnline.extras(context, game, false, address, password)) }
        })
    }
}

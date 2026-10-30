package com.retroforge.pcplayer

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroforge.pcplayer.net.PcLan
import com.retroforge.pcplayer.net.PlayTogetherService
import com.retroforge.pcplayer.net.Upnp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * "Play together" in the PC player's menu: a Windows game's LAN mode with other RetroForge devices (or PCs).
 *
 * - **Same Wi-Fi:** nothing to set up for most games (their LAN mode finds the others); Steam games need gbe_fork's
 *   lobbies on, which takes a restart.
 * - **Over the internet:** one device hosts a virtual LAN ([PcLan]) and opens its router's port with UPnP, as the
 *   retro player's netplay does; the others type its address. The game sees the others at 10.77.0.x.
 *
 * While it's on, the game keeps running with the menu open or another app in front ([PlayTogetherService]):
 * pausing it would hold up (or drop) the other players. [onActive] tells the player when that starts and stops.
 *
 * [restart] restarts the game (same player, same virtual LAN) with Steam lobbies on or off.
 */
class PlayTogether(
    private val context: Context,
    private val scope: CoroutineScope,
    private val runtimeRoot: File,
    private val title: String,
    private val restart: (lobbies: Boolean) -> Unit,
    private val onActive: (Boolean) -> Unit,
) {
    private enum class Mode { Off, SameWifi, Internet }

    /** Whether the game uses Steam, and whether it was started with Steam lobbies on. */
    var steamGame = false
    var lobbies by mutableStateOf(false)
    private var modeState by mutableStateOf(Mode.Off)
    private var mode: Mode
        get() = modeState
        set(value) {
            val was = modeState != Mode.Off
            modeState = value
            val now = value != Mode.Off
            if (was == now) return
            if (now) PlayTogetherService.start(context, title) else PlayTogetherService.stop(context)
            onActive(now)
        }
    private var status by mutableStateOf("")
    private var routerNote by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var joinAddress by mutableStateOf("")
    private var lan: PcLan? = null
    private var router: Upnp? = null
    private var watcher: Job? = null
    private var multicast: WifiManager.MulticastLock? = null

    val active get() = mode != Mode.Off

    init {
        // A virtual LAN left behind by a player that didn't close properly would reroute this game's traffic.
        PcLan.configFile(runtimeRoot).delete()
    }

    private val name: String
        get() = context.getSharedPreferences("netplay", Context.MODE_PRIVATE).getString("name", null)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private fun wifiAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }.getOrNull()

    /** Some phones drop broadcasts (how LAN games find each other) unless an app holds a multicast lock. */
    private fun holdMulticast() {
        if (multicast != null) return
        multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-pc-lan")?.apply { setReferenceCounted(false); runCatching { acquire() } }
    }

    private fun sameWifi() {
        stop()
        mode = Mode.SameWifi
        holdMulticast()
        status = "This device is ${wifiAddress()?.let { "at $it " } ?: ""}on your Wi-Fi. Open the game's LAN or " +
            "multiplayer menu on both devices; they should find each other."
        if (steamGame && !lobbies) restart(true)
    }

    private fun host() {
        stop()
        mode = Mode.Internet
        busy = true
        status = "Starting…"
        scope.launch {
            val started = runCatching { withContext(Dispatchers.IO) { PcLan.host(PcLan.configFile(runtimeRoot), name) } }
            busy = false
            started.onSuccess { watch(it) }.onFailure {
                mode = Mode.Off
                status = "Couldn't host: ${it.message ?: "port ${PcLan.PORT} is in use"}."
                return@launch
            }
            routerNote = "Checking whether friends on the internet can reach this device…"
            routerNote = withContext(Dispatchers.IO) { openRouter() }
            if (steamGame && !lobbies) restart(true)
        }
    }

    private fun openRouter(): String {
        val local = wifiAddress()
        val found = Upnp.find()
        if (found != null && local != null && found.open(PcLan.PORT, local, "TCP")) {
            found.open(PcLan.PORT, local, "UDP")
            router = found
            val outside = found.externalAddress()
            return if (outside != null) "Friends on the internet join at $outside (your router opened the port). " +
                "On the same Wi-Fi they can use $local."
            else "Your router opened the port. Friends on the same Wi-Fi can join at $local."
        }
        return "Your router didn't let RetroForge open port ${PcLan.PORT} (TCP and UDP) for friends on the internet: " +
            "send it to ${local ?: "this device"} in the router's settings. Friends on the same Wi-Fi can join at ${local ?: "this device's address"}."
    }

    private fun join() {
        val address = joinAddress.trim().substringBefore(':').ifBlank { return }
        stop()
        mode = Mode.Internet
        busy = true
        status = "Connecting to $address…"
        scope.launch {
            val joined = runCatching { withContext(Dispatchers.IO) { PcLan.join(PcLan.configFile(runtimeRoot), name, address) } }
            busy = false
            joined.onSuccess {
                watch(it)
                if (steamGame && !lobbies) restart(true)
            }.onFailure {
                mode = Mode.Off
                status = "Couldn't reach $address. Check the address, and that the host chose \"Host over the internet\" first."
            }
        }
    }

    private fun watch(started: PcLan) {
        lan = started
        holdMulticast()
        watcher = scope.launch {
            started.status.collect { s ->
                status = when (s) {
                    is PcLan.Status.Starting -> "${s.step}…"
                    is PcLan.Status.Failed -> s.message
                    is PcLan.Status.Up -> if (s.members.size < 2) "Hosting. Waiting for a friend to join…"
                    else "Playing together: " + s.members.joinToString(", ") {
                        (if (PcLan.address(it.index) == s.address) "you" else it.name) + " (${PcLan.address(it.index)})"
                    } + ". Open the game's LAN or multiplayer menu: the others are on its local network."
                }
            }
        }
    }

    /** Ends playing together (the game keeps running). */
    fun stop() {
        watcher?.cancel()
        watcher = null
        lan?.let { Thread { it.close() }.start() }
        lan = null
        router?.let { r -> Thread { r.close(PcLan.PORT, "TCP"); r.close(PcLan.PORT, "UDP") }.start() }
        router = null
        routerNote = null
        runCatching { multicast?.release() }
        multicast = null
        mode = Mode.Off
        status = ""
    }

    @Composable
    fun Panel(button: @Composable (onClick: () -> Unit, content: @Composable () -> Unit) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Play together", color = Color.White, fontWeight = FontWeight.Bold)
            Hint(
                "Play this game's LAN or multiplayer mode with other RetroForge devices or PCs." +
                    if (steamGame) " Steam games use RetroForge's Steam lobbies; turning them on restarts the game." else "",
            )
            if (status.isNotEmpty()) Text(status, color = Color(0xFFFF8A1F), fontSize = 13.sp, textAlign = TextAlign.Center)
            routerNote?.let { Hint(it) }
            if (busy) return@Column
            if (mode == Mode.Off) {
                button({ sameWifi() }) { Text("Same Wi-Fi") }
                button({ host() }) { Text("Host over the internet") }
                OutlinedTextField(
                    value = joinAddress,
                    onValueChange = { joinAddress = it },
                    label = { Text("Host's address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                button({ join() }) { Text("Join over the internet") }
            } else {
                button({ stop() }) { Text("Stop playing together") }
            }
            if (lobbies && mode == Mode.Off) button({ restart(false) }) { Text("Turn Steam lobbies off (restarts)") }
        }
    }

    @Composable
    private fun Hint(text: String) = Text(text, color = Color(0xFFAAAAB4), fontSize = 12.sp, textAlign = TextAlign.Center)

    /** When the player closes. */
    fun close() = stop()
}

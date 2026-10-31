package com.retroforge.app.player

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.retroforge.player.NativePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The RetroAchievements account: name and sign-in token (never the password), in their own preferences file so the
 * player's process reads them fresh when a game starts.
 */
class RaAccount(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("retroachievements", Context.MODE_PRIVATE)

    val user: String get() = prefs.getString("user", "").orEmpty()
    val token: String get() = prefs.getString("token", "").orEmpty()
    val displayName: String get() = prefs.getString("display_name", "").orEmpty().ifEmpty { user }
    val points: Int get() = prefs.getInt("points", 0)
    val signedIn: Boolean get() = user.isNotEmpty() && token.isNotEmpty()

    /** Achievements on in the built-in player (on once signed in). */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    /** Hardcore: no state loading or cheats, and unlocks count for the hardcore score. */
    var hardcore: Boolean
        get() = prefs.getBoolean("hardcore", false)
        set(value) = prefs.edit { putBoolean("hardcore", value) }

    fun signedIn(user: String, token: String, displayName: String, points: Int) =
        prefs.edit { putString("user", user); putString("token", token); putString("display_name", displayName); putInt("points", points) }

    fun signOut() = prefs.edit { remove("user"); remove("token"); remove("display_name"); remove("points") }
}

/** RetroForge's system ids to RetroAchievements' console ids (rcheevos' rc_consoles.h); 0 where it has none. */
object RaConsoles {
    private val ids = mapOf(
        "megadrive" to 1, "megadrive-msu" to 1, "n64" to 2, "snes" to 3, "snes-msu1" to 3, "satellaview" to 3, "sufami" to 3,
        "gb" to 4, "gba" to 5, "gbc" to 6, "nes" to 7, "pcengine" to 8, "supergrafx" to 8, "megacd" to 9, "sega32x" to 10,
        "mastersystem" to 11, "psx" to 12, "lynx" to 13, "ngp" to 14, "ngpc" to 14, "gamegear" to 15, "gamecube" to 16,
        "jaguar" to 17, "nds" to 18, "wii" to 19, "ps2" to 21, "odyssey2" to 23, "pokemini" to 24, "atari2600" to 25,
        "dos" to 26, "fbneo" to 27, "mame" to 27, "cps1" to 27, "cps2" to 27, "cps3" to 27, "neogeo" to 27, "virtualboy" to 28,
        "msx1" to 29, "msx2" to 29, "msx2+" to 29, "msxturbor" to 29, "c64" to 30, "zx81" to 31, "oricatmos" to 32,
        "sg1000" to 33, "c20" to 34, "amiga500" to 35, "amiga1200" to 35, "atarist" to 36, "amstradcpc" to 37,
        "apple2" to 38, "saturn" to 39, "dreamcast" to 40, "psp" to 41, "cdi" to 42, "3do" to 43, "colecovision" to 44,
        "intellivision" to 45, "vectrex" to 46, "pc88" to 47, "pc98" to 48, "pcfx" to 49, "atari5200" to 50,
        "atari7800" to 51, "x68000" to 52, "wswan" to 53, "wswanc" to 53, "cassettevision" to 54, "scv" to 55,
        "neogeocd" to 56, "channelf" to 57, "fmtowns" to 58, "zxspectrum" to 59, "gw" to 60, "ngage" to 61, "3ds" to 62,
        "supervision" to 63, "x1" to 64, "tic80" to 65, "thomson" to 66, "megaduck" to 69, "arduboy" to 71, "wasm4" to 72,
        "arcadia" to 73, "vc4000" to 74, "pcenginecd" to 76, "jaguarcd" to 77, "uzebox" to 80, "fds" to 81,
    )

    fun of(system: String): Int = ids[system] ?: 0
}

/** Something RetroAchievements has to say during a game. */
sealed interface RaEvent {
    data class SignedIn(val user: String, val token: String, val displayName: String, val points: Int) : RaEvent
    data class SignInFailed(val reason: String) : RaEvent
    data class GameReady(val title: String, val badge: String, val unlocked: Int, val total: Int) : RaEvent
    data class NoGame(val reason: String) : RaEvent
    data class Unlocked(val title: String, val description: String, val points: Int, val badge: String) : RaEvent
    data class Progress(val title: String, val progress: String, val badge: String) : RaEvent
    data class Completed(val title: String) : RaEvent
    data class Leaderboard(val title: String, val score: String) : RaEvent
    data class Problem(val message: String) : RaEvent
    data object Offline : RaEvent
    data object Online : RaEvent
}

data class RaAchievement(
    val id: Int, val title: String, val description: String, val points: Int, val unlocked: Boolean,
    val badge: String, val progress: String, val percent: Float,
)

/**
 * Runs rcheevos' web requests (the native side has no HTTP of its own) and hands its events to [onEvent] on the
 * main thread. One per game, or one for signing in from Settings.
 */
class RaSession(private val scope: CoroutineScope, private val onEvent: (RaEvent) -> Unit) {
    private var pump: Job? = null
    private var userAgent = ""

    fun start(hardcore: Boolean) {
        NativePlayer.cheevosStart(hardcore)
        userAgent = "RetroForge/$APP_VERSION (Android ${Build.VERSION.RELEASE}) ${NativePlayer.cheevosUserAgentClause()}".trim()
        if (pump != null) return
        pump = scope.launch(Dispatchers.IO) {
            while (isActive) {
                for (line in NativePlayer.cheevosRequests()) {
                    val (id, url, type, post) = line.split(SEP).let { it + List(4 - minOf(it.size, 4)) { "" } }
                    launch { answer(id.toInt(), url, type, post) }
                }
                val events = NativePlayer.cheevosEvents().mapNotNull(::parse)
                if (events.isNotEmpty()) withContext(Dispatchers.Main) { events.forEach(onEvent) }
                delay(50)
            }
        }
    }

    fun signIn(user: String, password: String = "", token: String = "") = NativePlayer.cheevosLogin(user, password, token)

    fun loadGame(consoleId: Int) = NativePlayer.cheevosLoadGame(consoleId)

    fun achievements(): List<RaAchievement> = NativePlayer.cheevosList().mapNotNull { line ->
        val f = line.split(SEP)
        if (f.size < 9) null
        else RaAchievement(f[0].toInt(), f[1], f[2], f[3].toInt(), f[4] == "1", f[5], f[6], f[7].toFloatOrNull() ?: 0f)
    }

    fun richPresence(): String = NativePlayer.cheevosRichPresence()

    fun stop() {
        pump?.cancel()
        pump = null
        NativePlayer.cheevosUnloadGame()
    }

    private fun answer(id: Int, url: String, type: String, post: String) {
        val (status, body) = try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", userAgent)
            if (post.isNotEmpty()) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", type.ifEmpty { "application/x-www-form-urlencoded" })
                connection.outputStream.use { it.write(post.toByteArray()) }
            }
            val code = connection.responseCode
            val bytes = (if (code >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes() }
            connection.disconnect()
            code to bytes
        } catch (e: Exception) {
            -1 to null
        }
        NativePlayer.cheevosResponse(id, status, body)
    }

    private fun parse(line: String): RaEvent? {
        val f = line.split(SEP)
        fun at(i: Int) = f.getOrElse(i) { "" }
        return when (f[0]) {
            "login" -> if (at(1) == "1") RaEvent.SignedIn(at(3), at(4), at(5), at(6).toIntOrNull() ?: 0) else RaEvent.SignInFailed(at(2))
            "game" -> if (at(1) == "1") RaEvent.GameReady(at(4), at(5), at(6).toIntOrNull() ?: 0, at(7).toIntOrNull() ?: 0) else RaEvent.NoGame(at(2))
            "unlocked" -> RaEvent.Unlocked(at(2), at(3), at(4).toIntOrNull() ?: 0, at(5))
            "progress" -> RaEvent.Progress(at(2), at(3), at(4))
            "completed" -> RaEvent.Completed(at(1))
            "leaderboard" -> RaEvent.Leaderboard(at(1), at(2))
            "error" -> RaEvent.Problem(at(1))
            "disconnected" -> RaEvent.Offline
            "reconnected" -> RaEvent.Online
            else -> null
        }
    }

    companion object {
        private const val SEP = '\u001f'
        private val APP_VERSION = com.retroforge.app.BuildConfig.VERSION_NAME
    }
}

package com.retroforge.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties

/**
 * Kiosk mode and child lock: a PIN guards Settings, deleting files, the ROM Manager and emulator settings. The PIN
 * is kept only as a salted hash in the app's private files (`kiosk.properties`), read fresh each time so the player
 * process sees a change made in the app. Clearing RetroForge's storage in Android's settings removes it, so a
 * forgotten PIN never locks anyone out for good.
 */
object Kiosk {
    /** How long one right PIN keeps things open; leaving RetroForge locks it again sooner (see [lock]). */
    private const val UNLOCK_MS = 10 * 60_000L
    private const val TRIES = 5
    private const val WAIT_MS = 30_000L

    /** When the right PIN was last given in this process (elapsed time), 0 = locked. Compose state, so pages open up. */
    private var unlockedAt by mutableLongStateOf(0L)
    private var wrong = 0
    private var waitUntil = 0L

    private fun file(context: Context) = File(context.filesDir, "kiosk.properties")

    private fun read(context: Context) = Properties().apply { runCatching { file(context).inputStream().use { load(it) } } }

    fun isOn(context: Context): Boolean = read(context).getProperty("hash") != null

    /** True when nothing needs the PIN now: kiosk mode is off, or the PIN was given a short while ago. */
    fun isOpen(context: Context): Boolean =
        !isOn(context) || (unlockedAt != 0L && SystemClock.elapsedRealtime() - unlockedAt < UNLOCK_MS)

    /** Milliseconds left before another try is allowed, after too many wrong ones. */
    fun waitLeft(): Long = (waitUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    /** Checks [pin]; right opens things for a while, five wrong in a row make the next try wait 30 seconds. */
    fun unlock(context: Context, pin: String): Boolean {
        if (waitLeft() > 0) return false
        val stored = read(context)
        val salt = stored.getProperty("salt") ?: return true
        val right = MessageDigest.isEqual(hash(salt, pin).toByteArray(), stored.getProperty("hash").orEmpty().toByteArray())
        if (right) {
            wrong = 0
            unlockedAt = SystemClock.elapsedRealtime()
        } else if (++wrong >= TRIES) {
            wrong = 0
            waitUntil = SystemClock.elapsedRealtime() + WAIT_MS
        }
        return right
    }

    /** Turns kiosk mode on with [pin] (or changes the PIN). The one setting it stays open for now. */
    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val props = Properties().apply {
            setProperty("salt", salt)
            setProperty("hash", hash(salt, pin))
        }
        val part = File(file(context).path + ".part")
        part.outputStream().use { props.store(it, null) }
        if (!part.renameTo(file(context))) {
            file(context).delete()
            part.renameTo(file(context))
        }
        unlockedAt = SystemClock.elapsedRealtime()
    }

    fun turnOff(context: Context) {
        file(context).delete()
        unlockedAt = 0L
    }

    /** Locks again (RetroForge left the screen). */
    fun lock() {
        unlockedAt = 0L
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }
}

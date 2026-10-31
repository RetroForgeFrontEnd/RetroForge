package com.retroforge.app.player

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Swapping saves with the user's other devices on the same Wi-Fi ([PeerLink]): whether it's on, the paired devices,
 * and the server, which runs while RetroForge is on screen. When RetroForge opens (or comes back from a game) it
 * swaps newer saves with every paired device that's open too.
 */
object PeerSync {
    /** One swap with one device: its name and what happened. */
    data class Result(val name: String, val report: SaveSync.Report?, val error: Throwable?)

    private const val PREFS = "peer_sync"
    private var server: PeerLink.Server? = null
    private val syncing = AtomicBoolean(false)

    /** Goes up when a device is paired or forgotten, so screens showing the list read it again. */
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(context: Context) = prefs(context).getBoolean("on", false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) start(context) else stop()
        changes++
    }

    /** This device's id, made the first time. */
    fun myId(context: Context): String = prefs(context).getString("id", null) ?: UUID.randomUUID().toString().also {
        prefs(context).edit().putString("id", it).apply()
    }

    fun myName(context: Context) = Netplay.name(context)

    /** Paired devices, stored one per line: id, name and secret, split by tabs. */
    fun peers(context: Context): List<Peer> = prefs(context).getStringSet("peers", emptySet()).orEmpty().mapNotNull { line ->
        line.split('\t').takeIf { it.size == 3 }?.let { Peer(it[0], it[1], it[2]) }
    }.sortedBy { it.name.lowercase() }

    @Synchronized
    fun addPeer(context: Context, peer: Peer) {
        val kept = peers(context).filter { it.id != peer.id } + peer
        prefs(context).edit().putStringSet("peers", kept.map { "${it.id}\t${it.name.replace('\t', ' ')}\t${it.secret}" }.toSet()).apply()
        changes++
    }

    @Synchronized
    fun forget(context: Context, peer: Peer) {
        prefs(context).edit().putStringSet("peers", peers(context).filter { it.id != peer.id }.map { "${it.id}\t${it.name}\t${it.secret}" }.toSet()).apply()
        File(context.filesDir, "peer_sync/${peer.id}.txt").delete()
        changes++
    }

    /** Starts serving this device's saves, if swapping is on. */
    @Synchronized
    fun start(context: Context) {
        if (server != null || !isOn(context)) return
        val app = context.applicationContext
        server = runCatching {
            PeerLink.Server(
                myId(app), myName(app),
                files = { FilesTarget(SaveSync.folders(app)) },
                secretOf = { id -> peers(app).firstOrNull { it.id == id }?.secret },
                onPaired = { addPeer(app, it) },
            )
        }.getOrNull()
    }

    @Synchronized
    fun stop() {
        server?.close()
        server = null
    }

    /** A code for another device to pair with this one (until it's used, or [closePairing]). Null when the server isn't running. */
    fun openPairing(): String? = server?.let { s -> PeerLink.newCode().also { s.pairCode = it } }

    fun closePairing() {
        server?.pairCode = null
    }

    /** RetroForges on the network, paired or not. Call off the main thread. */
    fun find(context: Context, waitMs: Int = 1500): List<FoundPeer> {
        // Some phones drop broadcasts unless an app holds a multicast lock.
        val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-peer-sync")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        return try {
            PeerLink.find(myId(context), waitMs)
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** Pairs with [found] using the code it shows; true if it took. Call off the main thread. */
    fun pair(context: Context, found: FoundPeer, code: String): Boolean {
        val peer = PeerLink.pair(found.host, found.port, code, myId(context), myName(context)) ?: return false
        addPeer(context, peer)
        return true
    }

    /**
     * Swaps saves with every paired device that's on the network now. Empty if none were found, or a swap is already
     * going. Call off the main thread.
     */
    fun syncAll(context: Context): List<Result> {
        val paired = peers(context).associateBy { it.id }
        if (paired.isEmpty() || !syncing.compareAndSet(false, true)) return emptyList()
        try {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return find(context).mapNotNull { found ->
                val peer = paired[found.id] ?: return@mapNotNull null
                runCatching {
                    PeerLink.Remote(found.host, found.port, myId(context), peer.secret).use { remote ->
                        SaveSync(SaveSync.folders(context), remote, File(context.filesDir, "peer_sync/${peer.id}.txt"), File(base, "sync conflicts")).sync()
                    }
                }.fold({ Result(peer.name, it, null) }, { Result(peer.name, null, it) })
            }
        } finally {
            syncing.set(false)
        }
    }
}

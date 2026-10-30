package com.retroforge.app.network

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.content.Context
import android.provider.DocumentsContract
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/** A shared folder on a PC or NAS (SMB 2/3). The password stays in RetroForge's private storage on this device. */
data class NetworkShare(
    val id: String,
    val name: String,
    /** The PC's name or address, optionally with ":port". */
    val host: String,
    /** The shared folder's name, optionally followed by a folder inside it ("Media/Films"). */
    val share: String,
    val user: String,
    val password: String,
    val domain: String = "",
) {
    val shareName: String get() = share.trim('/', '\\').substringBefore('/').substringBefore('\\')
    /** The folder inside the share that's shown as its top, if one was given. */
    val base: String get() = share.trim('/', '\\').replace('\\', '/').substringAfter('/', "")
    val address: String get() = "\\\\$host\\${share.trim('/', '\\').replace('/', '\\')}"
    /** Reached through Tailscale (a 100.64–100.127 address or a .ts.net name), so it works away from home too. */
    val viaTailscale: Boolean get() = NetworkDrives.isTailscale(host.substringBefore(':'))
}

/** One entry in a share: a file or a folder, by its path from the share's top ("" for the top). */
data class NetworkEntry(val path: String, val name: String, val isFolder: Boolean, val size: Long, val modified: Long)

/**
 * RetroForge's network drives: the saved shares, and connections to them (kept open and reused). Android's folder
 * picker shows them through [NetworkDrivesProvider], so game and film folders on a share work like any other folder.
 */
object NetworkDrives {
    private const val PREFS = "network_drives"
    private const val KEY = "shares"

    fun authority(context: Context) = "${context.packageName}.network"

    /** A share as a folder RetroForge can add to its games or films without the folder picker (it's RetroForge's own). */
    fun folderUri(context: Context, share: NetworkShare): android.net.Uri =
        DocumentsContract.buildTreeDocumentUri(authority(context), "${share.id}/")

    // The list is a file rather than SharedPreferences: NetworkDrivesProvider runs in a process of its own, and each
    // process keeps its own cached copy of SharedPreferences, so a drive added in the app would stay unknown to it.
    private fun listFile(context: Context) = java.io.File(context.filesDir, "network_drives.json")

    fun all(context: Context): List<NetworkShare> {
        val file = listFile(context)
        val text = runCatching { file.readText() }.getOrNull()
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) // saved before the file
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { array.getJSONObject(it) }.map {
                NetworkShare(it.getString("id"), it.getString("name"), it.getString("host"), it.getString("share"),
                    it.optString("user"), it.optString("password"), it.optString("domain"))
            }
        }.getOrDefault(emptyList())
    }

    fun byId(context: Context, id: String) = all(context).firstOrNull { it.id == id }

    fun save(context: Context, share: NetworkShare) = write(context, all(context).filter { it.id != share.id } + share)

    fun remove(context: Context, id: String) {
        write(context, all(context).filter { it.id != id })
        synchronized(this) { open.remove(id)?.let { runCatching { it.close() } } }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    private fun write(context: Context, shares: List<NetworkShare>) {
        val array = JSONArray()
        shares.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("share", it.share)
                .put("user", it.user).put("password", it.password).put("domain", it.domain))
        }
        synchronized(this) {
            val file = listFile(context)
            val temp = java.io.File(file.path + ".new")
            temp.writeText(array.toString())
            temp.renameTo(file)
        }
        // Tell Android's folder picker the list changed.
        context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority(context)), null)
    }

    // ---- Connections ----

    private val client by lazy {
        SMBClient(
            SmbConfig.builder()
                .withSecurityProvider(BCSecurityProvider()) // Android's own crypto lacks MD4, which NTLM sign-in needs
                .withTimeout(20, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build(),
        )
    }
    private val open = mutableMapOf<String, DiskShare>()

    /** For saving a share's new address when its PC has moved; set by [NetworkDrivesProvider]. */
    @Volatile var appContext: Context? = null

    /**
     * An open connection to [share], made (or made again) when needed. When its PC doesn't answer at the saved address
     * (it got a new one from the router, or the phone moved from the PC's USB tethering to Wi-Fi), the PC is looked
     * for again on the network and the new address is saved, so games and films keep working.
     */
    @Synchronized
    fun connect(share: NetworkShare): DiskShare {
        open[share.id]?.let { if (it.isConnected) return it else runCatching { it.close() } }
        // Just found switched off: say so at once instead of searching the network again for every file asked for.
        if (System.currentTimeMillis() < (offUntil[share.id] ?: 0)) throw PcUnreachable(share)
        return try {
            openDisk(share)
        } catch (e: Exception) {
            if (!unreachable(e)) throw e
            val moved = relocate(share) ?: run {
                offUntil[share.id] = System.currentTimeMillis() + 30_000
                throw PcUnreachable(share, e)
            }
            offUntil.remove(share.id)
            // relocate() opened it while checking.
            open[share.id]?.takeIf { it.isConnected } ?: openDisk(moved)
        }
    }

    private val offUntil = mutableMapOf<Any, Long>()

    /** The share's PC didn't answer at its address and wasn't found elsewhere on the network. */
    class PcUnreachable(share: NetworkShare, cause: Throwable? = null) : java.io.IOException(
        if (share.viaTailscale) "Can't reach ${share.address}. Is Tailscale switched on on this device, and is the PC switched on with Tailscale running?"
        else "Can't reach ${share.address}. Is the PC switched on and on the same network as this device?",
        cause,
    )

    /** Tailscale's addresses (100.64.0.0/10) and its MagicDNS names. */
    fun isTailscale(host: String): Boolean {
        if (host.endsWith(".ts.net", ignoreCase = true)) return true
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        return parts.size == 4 && parts[0] == 100 && parts[1] in 64..127
    }

    private fun unreachable(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
        it is java.net.ConnectException || it is java.net.SocketTimeoutException || it is java.net.NoRouteToHostException ||
            it is java.net.UnknownHostException
    }

    /** The PC that now has [share]'s folder: computers on the network sharing files, tried for the same folder. */
    private fun relocate(share: NetworkShare): NetworkShare? {
        val context = appContext ?: return null
        val port = share.host.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { ":$it" }.orEmpty()
        val computers = kotlinx.coroutines.runBlocking { NetworkSearch.find(context) }
            .filter { it.address != share.host.substringBefore(':') }
        // The PC with the share's own name first (it's named after the computer when added from the search).
        val ordered = computers.sortedByDescending { c -> c.name != null && share.name.contains(c.name, ignoreCase = true) }
        for (computer in ordered) {
            val moved = share.copy(host = computer.address + port)
            val works = runCatching { openDisk(moved).list(smbPath(moved, "")) }.isSuccess
            if (works) {
                // A Tailscale address is kept: it's the one that works away from home. The home address is used
                // for now (Tailscale switched off on the phone, say) without replacing it.
                if (!share.viaTailscale) save(context, moved)
                return moved
            }
        }
        return null
    }

    private fun openDisk(share: NetworkShare): DiskShare {
        val host = share.host.substringBefore(':')
        val port = share.host.substringAfter(':', "").toIntOrNull()
        val connection = if (port != null) client.connect(host, port) else client.connect(host)
        // No username: a guest login, then an anonymous one (many NAS drives allow one or the other).
        val logins = if (share.user.isBlank()) listOf(AuthenticationContext.guest(), AuthenticationContext.anonymous())
        else listOf(AuthenticationContext(share.user, share.password.toCharArray(), share.domain.ifBlank { null }))
        var failure: Exception? = null
        val session = logins.firstNotNullOfOrNull { auth ->
            runCatching { connection.authenticate(auth) }.onFailure { failure = it as? Exception }.getOrNull()
        } ?: throw java.io.IOException(explain(failure, share), failure)
        val disk = session.connectShare(share.shareName) as? DiskShare
            ?: throw java.io.IOException(str(R.string.network_drives_isn_t_a_shared_folder, share.shareName))
        open[share.id] = disk
        return disk
    }

    /** Windows' sign-in refusals in words people know, with what to do. */
    private fun explain(e: Exception?, share: NetworkShare): String {
        val m = e?.message.orEmpty()
        return when {
            "ACCOUNT_DISABLED" in m && share.user.isBlank() ->
                str(R.string.network_drives_this_computer_doesn_t_allow_guests)
            "LOGON_FAILURE" in m || "WRONG_PASSWORD" in m -> str(R.string.network_drives_the_username_or_password_wasn_t, share.host)
            "ACCOUNT_RESTRICTION" in m -> str(R.string.network_drives_that_account_has_no_password_and)
            else -> m.ifBlank { str(R.string.network_drives_couldn_t_sign_in) }
        }
    }

    /**
     * The folders [host] shares (not Windows' hidden admin shares like C$), signing in with [user] and [password]
     * (guest, then anonymous, when [user] is blank), so people pick a folder instead of typing its name.
     */
    fun sharedFolders(host: String, user: String, password: String): List<String> {
        val probe = NetworkShare("probe", "", host, "IPC$", user, password)
        val address = host.substringBefore(':')
        val port = host.substringAfter(':', "").toIntOrNull()
        val connection = if (port != null) client.connect(address, port) else client.connect(address)
        connection.use {
            val logins = if (user.isBlank()) listOf(AuthenticationContext.guest(), AuthenticationContext.anonymous())
            else listOf(AuthenticationContext(user, password.toCharArray(), null))
            var failure: Exception? = null
            val session = logins.firstNotNullOfOrNull { auth ->
                runCatching { connection.authenticate(auth) }.onFailure { failure = it as? Exception }.getOrNull()
            } ?: throw java.io.IOException(explain(failure, probe), failure)
            val transport = com.rapid7.client.dcerpc.transport.SMBTransportFactories.SRVSVC.getTransport(session)
            return com.rapid7.client.dcerpc.mssrvs.ServerService(transport).shares0.map { it.netName }
                .filter { !it.endsWith("$") }.sortedBy { it.lowercase() }
        }
    }

    /** Checks the share can be opened and read; returns how many entries its top holds. */
    fun test(share: NetworkShare): Int = runCatching { list(share, "") }.getOrElse { e ->
        if ("ACCESS_DENIED" in e.message.orEmpty()) throw java.io.IOException(
            str(R.string.network_drives_can_t_be_read_on_the, share.address, share.shareName), e,
        )
        throw e
    }.size

    private fun smbPath(share: NetworkShare, path: String) =
        listOf(share.base, path).filter { it.isNotEmpty() }.joinToString("/").replace('/', '\\')

    /**
     * Runs [block] on [share]'s connection, once more on a fresh one when it fails: a connection kept from earlier can
     * look open after the PC's network changed and then fail every request (seen as Eden's "No game found").
     * A file that really isn't there fails both times, so its error still comes through.
     */
    private fun <T> withDisk(share: NetworkShare, block: (DiskShare) -> T): T = try {
        block(connect(share))
    } catch (e: PcUnreachable) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("NetworkDrives", "${share.name}: ${e.javaClass.simpleName}: ${e.message}; trying a new connection", e)
        synchronized(this) { open.remove(share.id)?.let { runCatching { it.close() } } }
        block(connect(share))
    }

    fun list(share: NetworkShare, path: String): List<NetworkEntry> =
        withDisk(share) { it.list(smbPath(share, path)) }.filter { it.fileName != "." && it.fileName != ".." }.map {
            val folder = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            NetworkEntry(listOf(path, it.fileName).filter { p -> p.isNotEmpty() }.joinToString("/"), it.fileName, folder,
                it.endOfFile, it.lastWriteTime.toEpochMillis())
        }

    fun stat(share: NetworkShare, path: String): NetworkEntry {
        if (path.isEmpty()) return NetworkEntry("", share.name, true, 0, 0)
        val info = withDisk(share) { it.getFileInformation(smbPath(share, path)) }
        return NetworkEntry(path, path.substringAfterLast('/'), info.standardInformation.isDirectory,
            info.standardInformation.endOfFile, info.basicInformation.lastWriteTime.toEpochMillis())
    }

    fun openRead(share: NetworkShare, path: String): File = withDisk(share) {
        it.openFile(smbPath(share, path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
    }

    /** Every file under [path] (in all its folders); empty when the folder isn't there yet. */
    fun listAll(share: NetworkShare, path: String): List<NetworkEntry> {
        val here = runCatching { list(share, path) }.getOrElse { return emptyList() }
        return here.flatMap { if (it.isFolder) listAll(share, it.path) else listOf(it) }
    }

    fun readBytes(share: NetworkShare, path: String): ByteArray = openRead(share, path).use { it.inputStream.readBytes() }

    /**
     * Writes [bytes] to [path] (save syncing, the one place RetroForge writes to a share), making its folders first.
     * Returns the file's modified time on the share afterwards.
     */
    fun writeBytes(share: NetworkShare, path: String, bytes: ByteArray): Long {
        val disk = connect(share)
        var folder = ""
        for (part in path.split('/').dropLast(1)) {
            folder = if (folder.isEmpty()) part else "$folder/$part"
            if (!disk.folderExists(smbPath(share, folder))) disk.mkdir(smbPath(share, folder))
        }
        disk.openFile(
            smbPath(share, path), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null,
        ).use { file -> file.outputStream.use { it.write(bytes) } }
        return stat(share, path).modified
    }
}

/** A computer on the local network that shares files (answers on SMB's port 445). */
data class FoundComputer(val address: String, val name: String?)

/** Finds computers sharing files on the same Wi-Fi network, so nobody has to know their PC's address. */
object NetworkSearch {
    /**
     * Every address on the networks this device is on (Wi-Fi, Ethernet, USB tethering: each one's /24) that accepts SMB
     * connections, with its name where known.
     */
    suspend fun find(context: Context): List<FoundComputer> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val own = ownAddresses()
        if (own.isEmpty()) return@withContext emptyList()
        val gate = kotlinx.coroutines.sync.Semaphore(32)
        kotlinx.coroutines.coroutineScope {
            own.map { it.substringBeforeLast('.') }.distinct().flatMap { prefix -> (1..254).map { "$prefix.$it" } }.filter { it !in own }.map { address ->
                async {
                    gate.acquire()
                    try {
                        // The gateway (".1", or ".2" on the Android emulator, where it's the PC itself) gets longer to answer.
                        val patient = address.endsWith(".1") || address.endsWith(".2")
                        if (!answers(address, if (patient) 1500 else 700)) null
                        else FoundComputer(address, runCatching { java.net.InetAddress.getByName(address).canonicalHostName }
                            .getOrNull()?.takeIf { it != address }?.substringBefore('.'))
                    } finally {
                        gate.release()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun answers(address: String, timeoutMs: Int): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(address, 445), timeoutMs) }
        true
    }.getOrDefault(false)

    /** This device's local IPv4 addresses on every network interface that's up (not loopback or link-local). */
    internal fun ownAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<java.net.Inet4Address>()
            .filter { !it.isLinkLocalAddress && it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())
}

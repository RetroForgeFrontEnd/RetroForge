package com.retroforge.app.network

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

    fun all(context: Context): List<NetworkShare> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
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

    /** An open connection to [share], made (or made again) when needed. */
    @Synchronized
    fun connect(share: NetworkShare): DiskShare {
        open[share.id]?.let { if (it.isConnected) return it else runCatching { it.close() } }
        val host = share.host.substringBefore(':')
        val port = share.host.substringAfter(':', "").toIntOrNull()
        val connection = if (port != null) client.connect(host, port) else client.connect(host)
        val auth = if (share.user.isBlank()) AuthenticationContext.guest()
        else AuthenticationContext(share.user, share.password.toCharArray(), share.domain.ifBlank { null })
        val disk = connection.authenticate(auth).connectShare(share.shareName) as? DiskShare
            ?: throw java.io.IOException("${share.shareName} isn't a shared folder")
        open[share.id] = disk
        return disk
    }

    /** Checks the share can be opened and read; returns how many entries its top holds. */
    fun test(share: NetworkShare): Int = list(share, "").size

    private fun smbPath(share: NetworkShare, path: String) =
        listOf(share.base, path).filter { it.isNotEmpty() }.joinToString("/").replace('/', '\\')

    fun list(share: NetworkShare, path: String): List<NetworkEntry> =
        connect(share).list(smbPath(share, path)).filter { it.fileName != "." && it.fileName != ".." }.map {
            val folder = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            NetworkEntry(listOf(path, it.fileName).filter { p -> p.isNotEmpty() }.joinToString("/"), it.fileName, folder,
                it.endOfFile, it.lastWriteTime.toEpochMillis())
        }

    fun stat(share: NetworkShare, path: String): NetworkEntry {
        if (path.isEmpty()) return NetworkEntry("", share.name, true, 0, 0)
        val info = connect(share).getFileInformation(smbPath(share, path))
        return NetworkEntry(path, path.substringAfterLast('/'), info.standardInformation.isDirectory,
            info.standardInformation.endOfFile, info.basicInformation.lastWriteTime.toEpochMillis())
    }

    fun openRead(share: NetworkShare, path: String): File = connect(share).openFile(
        smbPath(share, path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null,
    )
}

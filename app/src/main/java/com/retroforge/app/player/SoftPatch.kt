package com.retroforge.app.player

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.retroforge.patch.PatchOutcome
import com.retroforge.patch.RomPatcher
import java.io.File

/**
 * Soft patching, as RetroArch does it: a patch named like the game ("Game.bps", "Game.ups" or "Game.ips") in the same
 * folder is applied as the game loads, so a translation or fix plays without the game file ever changing.
 */
object SoftPatch {
    class Found(val name: String, val bytes: ByteArray)

    private val kinds = listOf("bps", "ups", "ips")
    /** Games on disc (or in disc formats) aren't patched: their patches are made for one track or need xdelta. */
    val discTypes = setOf("cue", "m3u", "gdi", "chd", "iso", "cso", "rvz", "pbp", "cdi", "ccd", "toc", "wbfs", "gcz")
    private const val MAX_BYTES = 64L * 1024 * 1024

    /**
     * The patch beside [game] ([fileName] is its name), or null. Only folders on the phone, SD cards and USB drives,
     * whose document ids are paths, so the next-door file can be named.
     */
    fun find(resolver: ContentResolver, game: Uri, fileName: String): Found? = runCatching {
        if (game.authority != "com.android.externalstorage.documents") return null
        val id = DocumentsContract.getDocumentId(game)
        val folder = if ('/' in id) id.substringBeforeLast('/') + "/" else id.substringBefore(':') + ":"
        val base = fileName.substringBeforeLast('.', fileName)
        for (kind in kinds) {
            val name = "$base.$kind"
            val uri = DocumentsContract.buildDocumentUriUsingTree(game, folder + name)
            val bytes = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { if (it.statSize > MAX_BYTES) null else resolver.openInputStream(uri)?.use { s -> s.readBytes() } }
            }.getOrNull() ?: continue
            return Found(name, bytes)
        }
        null
    }.getOrNull()

    /** Patches [rom] in place. False (and the file left as it was) when the patch doesn't fit this game. */
    fun apply(rom: File, patch: Found): Boolean =
        when (val out = RomPatcher.apply(rom.readBytes(), patch.bytes)) {
            is PatchOutcome.Success -> { rom.writeBytes(out.output); true }
            is PatchOutcome.Failure -> false
        }
}

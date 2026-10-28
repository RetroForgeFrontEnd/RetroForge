package com.retroforge.retro

import java.net.URLDecoder

/**
 * Turns a Storage Access Framework document from Android's own external-storage provider into the file path
 * an emulator can open. Document ids there are "<volume>:<path>": "primary" is internal shared storage and
 * any other volume id (e.g. "1A2B-3C4D") is an SD card or USB drive mounted at /storage/<id>.
 * Other providers (cloud drives, network shares) have no file path, so null is returned.
 */
object StoragePaths {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private val volumePattern = Regex("[A-Za-z0-9-]+")

    fun filePath(treeUri: String, documentId: String): String? {
        val authority = treeUri.removePrefix("content://").substringBefore('/')
        if (authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val decoded = URLDecoder.decode(documentId, "UTF-8")
        val volume = decoded.substringBefore(':', missingDelimiterValue = "")
        val relative = decoded.substringAfter(':', missingDelimiterValue = "")
        if (volume.isEmpty() || !volumePattern.matches(volume)) return null
        if (relative.split('/').any { it == ".." }) return null
        val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return if (relative.isEmpty()) base else "$base/$relative"
    }
}

package com.retroforge.hacks

import kotlinx.serialization.Serializable

/**
 * A RetroForge hack catalogue (format documented in docs/hack-catalogue-format.md).
 * Catalogues describe hacks and link to their official pages; they never contain ROMs or patch data.
 */
@Serializable
data class HackCatalogue(
    val format: String,
    val version: Int,
    val id: String,
    val name: String,
    val description: String? = null,
    val maintainer: String? = null,
    val updated: String? = null,
    val hacks: List<HackEntry> = emptyList(),
)

@Serializable
data class HackEntry(
    val id: String,
    val title: String,
    /** RetroForge system id, matching RetroBat/ES-DE folder names (e.g. "nes", "snes", "gba"). */
    val system: String,
    val baseGame: String,
    /** The catalogue maintainer's own short summary, not text copied from the hack's page. */
    val summary: String,
    val pageUrl: String,
    /** "owner/repo" when the hack publishes its patch as GitHub releases: RetroForge can download it directly. */
    val patchRepo: String? = null,
    val authors: List<String> = emptyList(),
    val version: String? = null,
    val released: String? = null,
    val tags: List<String> = emptyList(),
    val baseRom: BaseRom? = null,
    /** Ids of hacks in the same catalogue that must be applied first, in order. */
    val requires: List<String> = emptyList(),
)

@Serializable
data class BaseRom(
    val name: String? = null,
    val crc32: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
    val note: String? = null,
)

/** One hack as shown in the app, tagged with the catalogue it came from. */
data class ListedHack(val sourceId: String, val sourceName: String, val entry: HackEntry) {
    val key: String get() = "$sourceId/${entry.id}"
}

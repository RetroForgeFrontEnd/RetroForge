package com.retroforge.app.ui.media

/** A song: from Android's music library (with its tags) or a file in the user's music folder (named from its file and folders). */
data class Track(
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String,
    /** Its number on the album, when known. */
    val number: Int?,
    /** Album art: the album's cover.jpg / folder.jpg, or Android's own for its music library. */
    val art: String?,
    /** Songs with the same key are one album (the same folder, or the same album in Android's library). */
    val albumKey: String,
)

data class Album(val key: String, val title: String, val artist: String?, val art: String?, val tracks: List<Track>)

object Music {
    /** What RetroForge's player (ExoPlayer) plays. */
    val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav")

    /** A folder's own album art, as music players and Windows save it. */
    val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart")

    private val leadingNumber = Regex("""^\s*(?:[0-9]{1,2}-)?([0-9]{1,3})\s*(?:[-._)]\s*|\s+)""")

    /**
     * A song named only by its file and folders: "Artist/Album/03 - Title.mp3", "Album/03. Artist - Title.flac" or
     * "Title.ogg". The folder is the album; the folder above it, if any, the artist, unless the file name gives one.
     */
    fun parse(uri: String, fileName: String, folder: String?, parentFolder: String?, folderKey: String, art: String? = null): Track {
        var name = fileName.substringBeforeLast('.').replace('_', ' ').trim()
        val number = leadingNumber.find(name)?.let { m ->
            name = name.substring(m.range.last + 1).trim()
            m.groupValues[1].toIntOrNull()
        }
        var artist = parentFolder?.takeIf { it.isNotBlank() }
        val dash = name.indexOf(" - ")
        if (dash > 0) {
            artist = name.substring(0, dash).trim()
            name = name.substring(dash + 3).trim()
        }
        val title = name.ifEmpty { fileName.substringBeforeLast('.') }
        return Track(uri, title, artist, folder?.takeIf { it.isNotBlank() } ?: title, number, art, folderKey)
    }

    /** The songs as albums, by artist then album, each album's songs in their order on it. */
    fun albums(tracks: List<Track>): List<Album> = tracks.groupBy { it.albumKey }.map { (key, songs) ->
        val sorted = songs.sortedWith(compareBy<Track>({ it.number ?: Int.MAX_VALUE }, { it.title.lowercase() }))
        // An album by several artists (a compilation) shows none rather than whichever came first.
        val artists = sorted.mapNotNull { it.artist }.distinct()
        Album(key, sorted.first().album, artists.singleOrNull(), sorted.firstNotNullOfOrNull { it.art }, sorted)
    }.sortedWith(compareBy({ it.artist?.lowercase() ?: "￿" }, { it.title.lowercase() }))

    /** Android stores a song's number on the album as disc × 1000 + track. */
    fun trackNumber(stored: Int): Int? = (stored % 1000).takeIf { it > 0 }
}

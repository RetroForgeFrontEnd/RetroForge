package com.retroforge.app.ui.media

/** A video file in the user's films folder. */
data class Film(
    val uri: String,
    val fileName: String,
    val title: String,
    val year: Int?,
    /** Side-by-side or top-and-bottom 3D: each eye has its own half, so it plays as it is in a Cardboard viewer. */
    val is3d: Boolean,
    val modified: Long,
    /** A poster saved next to the film (Kodi/Plex style: Film-poster.jpg, Film.jpg, or poster.jpg in its own folder). */
    val posterUri: String? = null,
    /** Its .nfo file (Film.nfo or movie.nfo): story, rating, year, runtime, as Media Companion or tinyMediaManager save them. */
    val nfoUri: String? = null,
)

/** What a Kodi-style .nfo file says about a film. */
data class NfoInfo(val title: String?, val plot: String?, val rating: Double?, val year: String?, val runtime: Int?, val imdb: String?)

object Films {
    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "m4v", "webm", "mov", "ts", "wmv", "mpg", "mpeg")

    private val year = Regex("""(?<![0-9])(19[2-9][0-9]|20[0-9][0-9])(?![0-9])""")

    // Release tags that end a film's title in a file name.
    private val tags = Regex(
        """\b(2160p|1080p|720p|480p|4k|uhd|hdr|bluray|blu-ray|brrip|bdrip|webrip|web-dl|web|dvdrip|hdtv|x264|x265|h264|h265|hevc|""" +
            """aac|ac3|dts|remux|proper|repack|extended|unrated|3d|sbs|hsbs|half-sbs|tab|htab|hou)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val threeD = Regex("""\b(3d|sbs|hsbs|half[-. ]?sbs|tab|htab|half[-. ]?ou|hou)\b""", RegexOption.IGNORE_CASE)

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * The poster and .nfo for [fileName] among the other files in its folder ([siblings], names only), the way Kodi,
     * Plex and Jellyfin find them. Folder-wide names (poster.jpg, movie.nfo) only count when the folder holds just
     * this one film, since they'd belong to all of them otherwise.
     */
    fun companions(fileName: String, siblings: Collection<String>, filmsInFolder: Int): Pair<String?, String?> {
        val base = fileName.substringBeforeLast('.')
        val byName = siblings.associateBy { it.lowercase() }
        fun find(names: List<String>) = names.firstNotNullOfOrNull { byName[it.lowercase()] }
        val own = filmsInFolder == 1
        val posters = IMAGE_EXTENSIONS.flatMap { listOf("$base-poster.$it", "$base.$it") } +
            if (own) IMAGE_EXTENSIONS.flatMap { listOf("poster.$it", "folder.$it", "cover.$it") } else emptyList()
        val nfos = listOf("$base.nfo") + if (own) listOf("movie.nfo") else emptyList()
        return find(posters) to find(nfos)
    }

    /** Reads a Kodi-style movie .nfo (as Media Companion, tinyMediaManager, Jellyfin and Emby write). Null if it isn't one. */
    fun nfo(xml: String): NfoInfo? = runCatching {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(xml.trim().toByteArray()))
        val root = doc.documentElement.takeIf { it.tagName == "movie" } ?: return null
        fun text(tag: String) = root.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifEmpty { null }
        // Newer files: <ratings><rating default="true"><value>; older: <rating>7.5</rating>.
        val ratings = root.getElementsByTagName("rating")
        val rating = (0 until ratings.length).map { ratings.item(it) as org.w3c.dom.Element }
            .sortedByDescending { it.getAttribute("default") == "true" }
            .firstNotNullOfOrNull { r -> (r.getElementsByTagName("value").item(0)?.textContent ?: r.textContent).trim().toDoubleOrNull() }
        val ids = root.getElementsByTagName("uniqueid")
        val imdb = (0 until ids.length).map { ids.item(it).textContent.trim() }.firstOrNull { it.startsWith("tt") }
            ?: text("imdbid")?.takeIf { it.startsWith("tt") } ?: text("id")?.takeIf { it.startsWith("tt") }
        NfoInfo(
            title = text("title"),
            plot = text("plot") ?: text("outline"),
            rating = rating?.takeIf { it > 0 },
            year = text("premiered")?.take(4) ?: text("year"),
            runtime = text("runtime")?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it > 0 },
            imdb = imdb,
        )
    }.getOrNull()

    /** "The.Matrix.1999.1080p.BluRay.x264.mkv" -> "The Matrix", 1999. */
    fun parse(uri: String, fileName: String, modified: Long): Film {
        val base = fileName.substringBeforeLast('.')
        val spaced = base.replace('.', ' ').replace('_', ' ').replace(Regex("""\s+"""), " ").trim()
        val found = year.find(spaced)
        val tagAt = tags.find(spaced)?.range?.first
        val cut = listOfNotNull(found?.range?.first, tagAt).minOrNull() ?: spaced.length
        val title = spaced.substring(0, cut).trim(' ', '-', '(', '[').ifEmpty { spaced }
        return Film(uri, fileName, title, found?.value?.toInt(), threeD.containsMatchIn(spaced), modified)
    }
}

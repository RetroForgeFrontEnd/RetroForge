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
)

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

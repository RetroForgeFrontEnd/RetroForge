package com.retroforge.library

/**
 * Every match of this regex in [input], found with one matcher. Kotlin's findAll makes a new matcher for each match,
 * and on Android every new matcher copies the whole input into ICU, so on a big text (libretro's 4 MB NES box art
 * index, a DAT file, a long gamelist.xml) it copies gigabytes and runs for minutes.
 */
fun Regex.matchesIn(input: CharSequence): Sequence<java.util.regex.MatchResult> = sequence {
    val matcher = toPattern().matcher(input)
    while (matcher.find()) yield(matcher.toMatchResult())
}

package com.retroforge.app

/**
 * Which build this is. The "play" build (Google Play) leaves out what Play's rules don't allow or put at risk:
 * Switch (Nintendo has had Switch emulators taken down), updating itself and installing other apps outside Play,
 * outside donation links, and one-tap downloads of fan texture packs. The "full" build (GitHub) has everything.
 * See docs/PLAY_STORE.md.
 */
object Store {
    val play: Boolean = BuildConfig.PLAY_STORE

    /** Systems the Play build has no emulator for. */
    val hiddenSystems: Set<String> = if (play) setOf("switch") else emptySet()

    /** Standalone emulators the Play build doesn't offer. */
    val hiddenEmulators: Set<String> = if (play) setOf("RETROFORGE_SWITCH", "EDEN") else emptySet()
}

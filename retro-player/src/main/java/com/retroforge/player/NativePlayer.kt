package com.retroforge.player

import android.view.Surface

/**
 * RetroForge's own libretro frontend (retro-player/src/main/cpp/player.cpp). One game per process:
 * [openCore], [loadGame], [start], then [stop]/[close] when the player closes.
 */
object NativePlayer {
    init {
        System.loadLibrary("retroforge_player")
    }

    /** Null when the core loaded, otherwise what went wrong. */
    external fun openCore(corePath: String, systemDir: String, saveDir: String, optionKeys: Array<String>, optionValues: Array<String>): String?

    /** needFullpath ("1"/"0"), blockExtract, library name, version, valid extensions ("gba|gb|zip"). */
    private external fun systemInfo(): Array<String?>

    fun coreInfo(): CoreInfo = systemInfo().let { i ->
        CoreInfo(
            needsFile = i[0] == "1",
            blockExtract = i[1] == "1",
            name = i[2].orEmpty(),
            version = i[3].orEmpty(),
            extensions = i[4].orEmpty().lowercase().split('|').filter { it.isNotBlank() }.toSet(),
        )
    }

    /** [path] is the game's file (or a name with the right extension when [fd] supplies the bytes). */
    external fun loadGame(path: String, fd: Int, fromFileIntoMemory: Boolean): Boolean

    /** fps, sample rate, base width, base height, aspect ratio. */
    private external fun avInfo(): FloatArray

    fun video(): Video = avInfo().let { Video(fps = it[0], width = it[2].toInt(), height = it[3].toInt(), aspect = it[4]) }

    external fun start()
    external fun setPaused(paused: Boolean)
    external fun setFastForward(on: Boolean)
    external fun setSurface(surface: Surface?)
    external fun setButtons(port: Int, mask: Int)
    external fun setAnalog(port: Int, stick: Int, x: Int, y: Int)

    private external fun coreOptions(): Array<String>

    /** The core's own settings (what RetroArch calls core options), in the core's order. */
    fun options(): List<CoreOption> = coreOptions().toList().chunked(4).map { (key, label, choices, current) ->
        CoreOption(key, label, choices.split('|').filter { it.isNotEmpty() }, current)
    }

    /** What's plugged into [port]: a libretro device id ([PlayerCores.portDevice]). */
    external fun setPortDevice(port: Int, device: Int)

    /** Changes a core option while the game runs. */
    external fun setOption(key: String, value: String)

    /** Screen filter: [ScreenFilter.ordinal]. */
    external fun setFilter(filter: Int)

    /** Where the picture goes on the surface, in pixels from the top left ([Viewport.fit]). */
    external fun setViewport(x: Int, y: Int, w: Int, h: Int)

    private external fun discs(): IntArray

    /** (number of discs, current disc from 0), or null when the game has one disc / the core can't swap. */
    fun discInfo(): Pair<Int, Int>? = discs().let { (count, current) -> if (count > 1) count to current else null }

    /** Opens the tray, puts in disc [index] (from 0) and closes it. */
    external fun insertDisc(index: Int): Boolean

    /** Game volume, 1.0 = as the emulator made it; up to 2.0 for quiet systems. */
    external fun setVolume(volume: Float)

    /** Touchscreen: [x], [y] from 0 to 1 across the game picture. */
    external fun setPointer(x: Float, y: Float, down: Boolean)
    external fun reset()
    external fun saveRam(): ByteArray?
    external fun restoreSaveRam(data: ByteArray)
    external fun saveState(): ByteArray?
    external fun loadState(state: ByteArray): Boolean
    external fun stop()
    external fun close()
}

data class CoreInfo(val needsFile: Boolean, val blockExtract: Boolean, val name: String, val version: String, val extensions: Set<String>)

data class CoreOption(val key: String, val label: String, val choices: List<String>, val value: String)

data class Video(val fps: Float, val width: Int, val height: Int, val aspect: Float)

/** libretro's joypad button numbers, as bits in the mask passed to [NativePlayer.setButtons]. */
object Pad {
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15

    fun bit(button: Int) = 1 shl button
}

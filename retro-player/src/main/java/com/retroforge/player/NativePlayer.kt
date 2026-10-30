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
    /** Makes [path] read from [fd] (duplicated) for cores that open files through the player's VFS: games stream, no copy. */
    external fun addStream(path: String, fd: Int)
    /** Whether the open core asked for the player's VFS (so streamed paths reach it). Known right after openCore. */
    external fun vfsRequested(): Boolean
    /** Frames drawn on screen so far: the difference over a second is the FPS counter. */
    external fun framesShown(): Long

    /** fps, sample rate, base width, base height, aspect ratio. */
    private external fun avInfo(): FloatArray

    fun video(): Video = avInfo().let { Video(fps = it[0], width = it[2].toInt(), height = it[3].toInt(), aspect = it[4]) }

    external fun start()
    external fun setPaused(paused: Boolean)
    external fun setFastForward(on: Boolean)
    external fun setSurface(surface: Surface?)

    /** A second display's surface (dual-screen devices): the picture's bottom half goes there. Null to stop. */
    external fun setSecondSurface(surface: Surface?)

    /** Cardboard VR: the picture once per eye, side by side. */
    external fun setVr(on: Boolean)

    /** The core draws both eyes side by side (Virtual Boy in 3D): each eye gets its half; outside VR only the left. */
    external fun setSideBySideSource(on: Boolean)
    external fun setButtons(port: Int, mask: Int)
    external fun setAnalog(port: Int, stick: Int, x: Int, y: Int)

    private external fun coreOptions(): Array<String>

    /** The core's own settings (what RetroArch calls core options), in the core's order. */
    fun options(): List<CoreOption> = coreOptions().toList().chunked(4).map { (key, label, choices, current) ->
        CoreOption(key, label, choices.split('|').filter { it.isNotEmpty() }, current)
    }

    /** The core's recent warnings and errors, oldest first (why a game wouldn't start). */
    external fun coreMessages(): Array<String>

    /** What the core calls player 1's buttons: libretro id and name, two entries per button. */
    external fun buttonNames(): Array<String>

    /** What's plugged into [port]: a libretro device id ([PlayerCores.portDevice]). */
    external fun setPortDevice(port: Int, device: Int)

    /** Changes a core option while the game runs. */
    external fun setOption(key: String, value: String)

    /** Screen filter: [ScreenFilter.ordinal]. */
    external fun setFilter(filter: Int)

    /** Where the picture goes on the surface, in pixels from the top left ([Viewport.fit]). */
    external fun setViewport(x: Int, y: Int, w: Int, h: Int)

    /** Turns on exactly these cheat codes (the core's own formats: Game Genie, Action Replay, GameShark …). False if the core has no cheats. */
    external fun applyCheats(codes: Array<String>): Boolean

    private external fun discs(): IntArray

    /** (number of discs, current disc from 0), or null when the game has one disc / the core can't swap. */
    fun discInfo(): Pair<Int, Int>? = discs().let { (count, current) -> if (count > 1) count to current else null }

    /** Opens the tray, puts in disc [index] (from 0) and closes it. */
    external fun insertDisc(index: Int): Boolean

    /** Game volume, 1.0 = as the emulator made it; up to 2.0 for quiet systems. */
    external fun setVolume(volume: Float)

    /** Touchscreen: [x], [y] from 0 to 1 across the game picture. */
    external fun setPointer(x: Float, y: Float, down: Boolean)
    /** A keyboard key ([RetroKeys] code) pressed or released, with the character it types and its modifiers. */
    external fun setKey(keycode: Int, down: Boolean, character: Int, modifiers: Int)
    /** Mouse movement since the last call (in game pixels) and the buttons held: bit 0 left, 1 right, 2 middle. */
    external fun setMouse(dx: Int, dy: Int, buttons: Int)
    external fun reset()
    external fun saveRam(): ByteArray?
    external fun restoreSaveRam(data: ByteArray)
    external fun saveState(): ByteArray?
    external fun loadState(state: ByteArray): Boolean

    // Netplay (netplay.h): two devices play this game together, the host as player 1. [gameId] must match on both.
    /** Starts listening on [port]; null when it did, else why not. */
    external fun netplayHost(port: Int, gameId: String, delayFrames: Int): String?
    /** Connects to a host in the background; [netplayState] and [netplayStatus] say how it's going. */
    external fun netplayJoin(address: String, port: Int, gameId: String, delayFrames: Int): String?
    external fun netplayLeave()
    /** 0 off, 1 hosting (waiting for player 2), 2 joining, 3 starting, 4 playing, 5 failed. */
    external fun netplayState(): Int
    external fun netplayStatus(): String
    /** 0 when this device is player 1 (the host), 1 when it's player 2. */
    external fun netplayPlayer(): Int

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

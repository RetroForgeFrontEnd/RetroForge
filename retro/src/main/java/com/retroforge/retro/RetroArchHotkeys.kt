package com.retroforge.retro

import com.retroforge.retro.R
import com.retroforge.core.ui.str
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** RetroArch's controller-independent button combos (RetroArch 1.22.2 input_defines.h, INPUT_COMBO_*). */
enum class HotkeyCombo(val value: Int, private val labelId: Int) {
    NONE(0, R.string.hotkey_off),
    L3_R3(2, R.string.hotkey_l3_r3),
    START_SELECT(4, R.string.hotkey_start_select),
    L1_R1(6, R.string.hotkey_l1_r1),
    HOLD_START(7, R.string.hotkey_hold_start),
    HOLD_SELECT(8, R.string.hotkey_hold_select),
    DOWN_SELECT(9, R.string.hotkey_down_select),
    L2_R2(10, R.string.hotkey_l2_r2),
    ;

    val label: String get() = str(labelId)

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/**
 * Gives RetroArch RetroForge's hotkeys without editing RetroArch's own settings: a small config in
 * Documents/RetroForge sets the combos and then `#include`s RetroArch's retroarch.cfg. RetroArch reads files top
 * down (first value wins) and, when it saves, writes changed settings above the include, so the user's own changes
 * made during a game are kept in this file too. Needs Android 10+ (MediaStore relative paths).
 */
class RetroArchHotkeys(private val context: Context) {

    /** Writes (or refreshes) the config and returns its path for RetroArch, or null to use RetroArch's own. */
    /** [fastForwardOnR2]: hold R2 to fast-forward, for systems whose own controllers had no R2 ([NO_R2_SYSTEMS]). */
    /** [resume]: save a state when the game closes and load it when it starts again (RetroArch's auto save/load). */
    fun prepare(install: RetroArchInstall, menu: HotkeyCombo, quit: HotkeyCombo, fastForwardOnR2: Boolean = false, resume: Boolean = false): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existing = find(collection)
            val previous = existing?.let { (uri, _) -> resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.orEmpty()
            val main = "/storage/emulated/0/Android/data/${install.packageName}/files/retroarch.cfg"
            val text = render(previous, main, menu, quit, fastForwardOnR2, resume)
            val (uri, name) = existing ?: run {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/RetroForge")
                }
                val created = resolver.insert(collection, values) ?: error(str(R.string.retro_arch_hotkeys_couldn_t_create_the_hotkey_file))
                created to (displayName(created) ?: FILE_NAME)
            }
            if (text != previous) resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error(str(R.string.retro_arch_hotkeys_couldn_t_write))
            "/storage/emulated/0/${Environment.DIRECTORY_DOCUMENTS}/RetroForge/$name"
        }.getOrNull()
    }

    private fun find(collection: Uri): Pair<Uri, String>? = context.contentResolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
        arrayOf("${Environment.DIRECTORY_DOCUMENTS}/RetroForge/", "retroarch-retroforge%"),
        null,
    )?.use { c -> if (c.moveToFirst()) Uri.withAppendedPath(collection, c.getLong(0).toString()) to c.getString(1) else null }

    private fun displayName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    companion object {
        const val FILE_NAME = "retroarch-retroforge.cfg"
        private val managed = setOf("input_menu_toggle_gamepad_combo", "input_quit_gamepad_combo", "quit_press_twice", "input_hold_fast_forward_btn", "savestate_auto_save", "savestate_auto_load")

        /** RetroArch on Android numbers controller buttons by Android key code; KeyEvent.KEYCODE_BUTTON_R2 is 105. */
        private const val BUTTON_R2 = 105

        /** Systems whose original controllers had no L2/R2, so R2 is free for fast-forward. */
        val NO_R2_SYSTEMS = setOf(
            "nes", "fds", "snes", "sfc", "snes-msu1", "satellaview", "sufami", "gb", "gbc", "gba", "megadrive", "genesis",
            "megadrive-msu", "mastersystem", "gamegear", "sg1000", "megacd", "segacd", "sega32x", "pcengine", "pcenginecd",
            "supergrafx", "atari2600", "atari5200", "atari7800", "lynx", "ngp", "ngpc", "wswan", "wswanc", "virtualboy",
            "pokemini", "colecovision", "intellivision", "vectrex", "msx1", "msx2", "odyssey2", "channelf", "supervision",
        )

        /**
         * RetroForge's settings first, then whatever RetroArch saved into this file before (kept as is), then the
         * include of RetroArch's own config last, so ours win and RetroArch's fill in the rest.
         */
        fun render(previous: String, mainConfig: String, menu: HotkeyCombo, quit: HotkeyCombo, fastForwardOnR2: Boolean = false, resume: Boolean = false): String {
            val kept = previous.lines().filter { line ->
                val t = line.trim()
                t.isNotEmpty() && !t.startsWith("#") && t.substringBefore('=').trim() !in managed
            }
            return buildString {
                appendLine("# Written by RetroForge: hotkeys for games started from RetroForge. Your RetroArch settings are included below.")
                appendLine("input_menu_toggle_gamepad_combo = \"${menu.value}\"")
                appendLine("input_quit_gamepad_combo = \"${quit.value}\"")
                appendLine("quit_press_twice = \"false\"")
                if (fastForwardOnR2) appendLine("input_hold_fast_forward_btn = \"$BUTTON_R2\"")
                appendLine("savestate_auto_save = \"$resume\"")
                appendLine("savestate_auto_load = \"$resume\"")
                kept.forEach(::appendLine)
                appendLine("#include \"$mainConfig\"")
            }
        }
    }
}

package com.retroforge.app.player

import android.content.Context
import java.io.File

/**
 * A game's save states in states/<system>: the quick slot (Save state in the pause menu), the resume point kept when the
 * game closes, and numbered slots. Each can have a picture beside it (<file>.png) and a name (<file>.name).
 * States belong to one emulator, so their file names include its id: <game>.<core>.state, <game>.<core>.auto.state,
 * <game>.<core>.s<n>.state.
 */
object SaveStates {
    enum class Kind { QUICK, AUTO, SLOT }

    data class State(val file: File, val core: String, val kind: Kind, val slot: Int, val name: String?) {
        val picture: File get() = File(file.path + ".png")
        val modified: Long get() = file.lastModified()
    }

    fun dir(context: Context, system: String) = File(context.getExternalFilesDir(null) ?: context.filesDir, "states/$system")

    fun slotFile(dir: File, saveBase: String, core: String, slot: Int) = File(dir, "$saveBase.$core.s$slot.state")

    /** This game's states, newest first. */
    fun list(dir: File, saveBase: String): List<State> {
        val pattern = Regex("""^${Regex.escape(saveBase)}\.([^.]+)\.(auto\.|s(\d+)\.)?state$""")
        return dir.listFiles().orEmpty().mapNotNull { file ->
            val m = pattern.matchEntire(file.name) ?: return@mapNotNull null
            val kind = when {
                m.groupValues[2] == "auto." -> Kind.AUTO
                m.groupValues[3].isNotEmpty() -> Kind.SLOT
                else -> Kind.QUICK
            }
            State(file, m.groupValues[1], kind, m.groupValues[3].toIntOrNull() ?: 0, nameOf(file))
        }.sortedByDescending { it.modified }
    }

    /** Its own name, else Quick save, Resume point or State n. */
    fun label(context: Context, state: State): String = state.name ?: when (state.kind) {
        Kind.QUICK -> context.getString(com.retroforge.app.R.string.states_quick)
        Kind.AUTO -> context.getString(com.retroforge.app.R.string.states_resume)
        Kind.SLOT -> context.getString(com.retroforge.app.R.string.states_slot, state.slot)
    }

    fun nextSlot(states: List<State>, core: String) = (states.filter { it.core == core && it.kind == Kind.SLOT }.maxOfOrNull { it.slot } ?: 0) + 1

    private fun nameOf(file: File) = File(file.path + ".name").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    fun rename(state: State, name: String) {
        val f = File(state.file.path + ".name")
        if (name.isBlank()) f.delete() else f.writeText(name.trim())
    }

    fun delete(state: State) {
        state.file.delete()
        state.picture.delete()
        File(state.file.path + ".name").delete()
    }
}

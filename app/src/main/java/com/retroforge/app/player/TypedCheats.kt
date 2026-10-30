package com.retroforge.app.player

/** A cheat code a game has built in (Doom's "iddqd"), sent to the emulator as a cheat. */
data class TypedCheat(val name: String, val code: String)

/** Built-in cheat codes, offered on the Cheats page: Doom and Doom II's (PrBoom takes them as libretro cheats). */
object TypedCheats {
    private val doom = listOf(
        TypedCheat("God mode", "iddqd"),
        TypedCheat("All weapons, ammo, keys and armour", "idkfa"),
        TypedCheat("All weapons and ammo (no keys)", "idfa"),
        TypedCheat("Walk through walls", "idclip"),
        TypedCheat("Whole map (on the automap; again for things)", "iddt"),
        TypedCheat("Berserk", "idbeholds"),
        TypedCheat("Invisibility", "idbeholdi"),
        TypedCheat("Light amplification", "idbeholdl"),
    )

    fun forSystem(system: String): List<TypedCheat> = if (system == "doom") doom else emptyList()
}

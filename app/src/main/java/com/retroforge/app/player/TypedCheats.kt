package com.retroforge.app.player

import com.retroforge.app.R
import com.retroforge.core.ui.str

/** A cheat code a game has built in (Doom's "iddqd"), sent to the emulator as a cheat. */
data class TypedCheat(val name: String, val code: String)

/** Built-in cheat codes, offered on the Cheats page: Doom and Doom II's (PrBoom takes them as libretro cheats). */
object TypedCheats {
    private val doom get() = listOf(
        TypedCheat(str(R.string.typed_cheats_god_mode), "iddqd"),
        TypedCheat(str(R.string.typed_cheats_all_weapons_ammo_keys_and_armour), "idkfa"),
        TypedCheat(str(R.string.typed_cheats_all_weapons_and_ammo_no_keys), "idfa"),
        TypedCheat(str(R.string.typed_cheats_walk_through_walls), "idclip"),
        TypedCheat(str(R.string.typed_cheats_whole_map_on_the_automap_again), "iddt"),
        TypedCheat(str(R.string.typed_cheats_berserk), "idbeholds"),
        TypedCheat(str(R.string.typed_cheats_invisibility), "idbeholdi"),
        TypedCheat(str(R.string.typed_cheats_light_amplification), "idbeholdl"),
    )

    fun forSystem(system: String): List<TypedCheat> = if (system == "doom") doom else emptyList()
}

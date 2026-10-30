package com.retroforge.pcplayer.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCloudSyncTest {

    @Test
    fun prefixesJoinTheWaySteamNamesFiles() {
        assertEquals("save.dat", SteamCloudSync.joinPrefix("", "save.dat"))
        assertEquals("%WinAppDataLocal%Game/Saved/slot1.sav", SteamCloudSync.joinPrefix("%WinAppDataLocal%Game/Saved/", "slot1.sav"))
        assertEquals("%WinAppDataLocal%Game/slot1.sav", SteamCloudSync.joinPrefix("%WinAppDataLocal%Game", "slot1.sav"))
        // A bare root has no separator after it.
        assertEquals("%GameInstall%save0.dat", SteamCloudSync.joinPrefix("%GameInstall%", "save0.dat"))
    }

    @Test
    fun cloudPatternsMatchLikeSteams() {
        val sav = SteamCloudSync.glob("*.sav")
        assertTrue(sav.matches("slot1.sav"))
        assertTrue(sav.matches("SLOT1.SAV"))
        assertFalse(sav.matches("slot1.sav.bak"))
        assertTrue(SteamCloudSync.glob("slot?.dat").matches("slot3.dat"))
        assertFalse(SteamCloudSync.glob("slot?.dat").matches("slot10.dat"))
        assertTrue(SteamCloudSync.glob("*").matches("anything at all.cfg"))
        // Regex characters in a pattern are plain characters.
        assertTrue(SteamCloudSync.glob("save(1).dat").matches("save(1).dat"))
    }
}

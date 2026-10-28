package com.retroforge.retro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePathsTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ARetroBatTest"

    @Test
    fun internalStorage() {
        assertEquals(
            "/storage/emulated/0/RetroBatTest/roms/snes/Star Fox (USA).zip",
            StoragePaths.filePath(tree, "primary:RetroBatTest/roms/snes/Star Fox (USA).zip"),
        )
    }

    @Test
    fun sdCardOrUsbDrive() {
        val usb = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ARetrobat%2Froms"
        assertEquals("/storage/1A2B-3C4D/Retrobat/roms/nes/Zelda.zip", StoragePaths.filePath(usb, "1A2B-3C4D:Retrobat/roms/nes/Zelda.zip"))
    }

    @Test
    fun otherProvidersAndUnsafeIdsHaveNoPath() {
        assertNull(StoragePaths.filePath("content://com.google.android.apps.docs.storage/tree/abc", "doc=123"))
        assertNull(StoragePaths.filePath(tree, "primary:../../data/x"))
        assertNull(StoragePaths.filePath(tree, "no-colon"))
        assertNull(StoragePaths.filePath(tree, "bad/volume:x"))
    }

    @Test
    fun everySystemWithCoresHasADefault() {
        listOf("nes", "snes", "gba", "megadrive", "psx", "n64").forEach { assertTrue(it, RetroArchCores.coresFor(it).isNotEmpty()) }
        assertTrue(RetroArchCores.coresFor("ps3").isEmpty())
    }
}

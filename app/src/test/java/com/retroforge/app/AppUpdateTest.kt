package com.retroforge.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `newer versions`() {
        assertTrue(AppUpdate.isNewer("0.7.0-beta", "0.6.0-beta"))
        assertTrue(AppUpdate.isNewer("0.6.1-beta", "0.6.0-beta"))
        assertTrue(AppUpdate.isNewer("1.0.0", "0.9.9-beta"))
        assertTrue(AppUpdate.isNewer("0.7.0", "0.7.0-beta"))
        assertFalse(AppUpdate.isNewer("0.6.0-beta", "0.6.0-beta"))
        assertFalse(AppUpdate.isNewer("0.5.1-beta", "0.6.0-beta"))
        assertFalse(AppUpdate.isNewer("0.7.0-beta", "0.7.0"))
    }
}

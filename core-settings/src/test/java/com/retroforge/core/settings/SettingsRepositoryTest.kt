package com.retroforge.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") },
    )

    @Test
    fun defaultsWhenEmpty() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(AppSettings(), repository().settings.first())
    }

    @Test
    fun persistsChanges() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        repo.setThemeMode(ThemeMode.LIGHT)
        repo.setShowPcSection(false)
        repo.acknowledgeNotice(1)
        assertEquals(AppSettings(ThemeMode.LIGHT, showPcSection = false, noticeAcknowledged = 1), repo.settings.first())
    }
}

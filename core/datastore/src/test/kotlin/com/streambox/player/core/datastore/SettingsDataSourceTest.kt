package com.streambox.player.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SettingsDataSourceTest {
    @TempDir
    lateinit var tempDir: File

    private fun TestScope.createDataSource() = SettingsDataSource(
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempDir, "test.preferences_pb") },
        ),
    )

    @Test
    fun `fresh install has defaults and incomplete onboarding`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()

        assertEquals(ThemeMode.System, source.settings.first().themeMode)
        assertFalse(source.settings.first().useDynamicColor)
        assertEquals(OnboardingState(), source.onboardingState.first())
        assertFalse(source.onboardingState.first().isComplete)
    }

    @Test
    fun `persists theme mode and dynamic color`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()

        source.setThemeMode(ThemeMode.Amoled)
        source.setDynamicColor(true)

        assertEquals(ThemeMode.Amoled, source.settings.first().themeMode)
        assertTrue(source.settings.first().useDynamicColor)
    }

    @Test
    fun `onboarding completes only after both steps`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()

        source.markWelcomeSeen()
        assertFalse(source.onboardingState.first().isComplete)

        source.acceptDisclaimer()
        assertTrue(source.onboardingState.first().isComplete)
    }
}

package com.streambox.player.feature.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeSettingsRepository()
    private val viewModel = SettingsViewModel(repository)

    @Test
    fun `emits stored settings`() = runTest {
        viewModel.uiState.test {
            assertEquals(AppSettings(), awaitSettings { true })
        }
    }

    @Test
    fun `selecting a theme persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onThemeModeSelected(ThemeMode.Amoled)
            assertEquals(ThemeMode.Amoled, awaitSettings { it.themeMode == ThemeMode.Amoled }.themeMode)
        }
    }

    @Test
    fun `toggling dynamic color persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onDynamicColorChanged(true)
            assertEquals(true, awaitSettings { it.useDynamicColor }.useDynamicColor)
        }
    }

    /** Skips Loading (and any stale states) until a Success matching [predicate] arrives. */
    private suspend fun ReceiveTurbine<SettingsUiState>.awaitSettings(
        predicate: (AppSettings) -> Boolean,
    ): AppSettings {
        while (true) {
            val item = awaitItem()
            if (item is SettingsUiState.Success && predicate(item.settings)) return item.settings
        }
    }
}

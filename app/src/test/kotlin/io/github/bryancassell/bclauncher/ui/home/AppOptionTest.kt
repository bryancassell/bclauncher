package io.github.bryancassell.bclauncher.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.data.fakeApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOptionTest {
    @Test
    fun everyEntryIsEnabledForAnAppTheUserInstalled() {
        val app = fakeApp("Plex")

        assertEquals(
            listOf(AppOption.Launch, AppOption.AppSettings, AppOption.Uninstall),
            AppOption.entries.filter { it.isEnabledFor(app) },
        )
    }

    @Test
    fun uninstallIsDisabledForASystemApp() {
        val app = fakeApp("YouTube").copy(isSystemApp = true)

        assertEquals(
            listOf(AppOption.Launch, AppOption.AppSettings),
            AppOption.entries.filter { it.isEnabledFor(app) },
        )
    }
}

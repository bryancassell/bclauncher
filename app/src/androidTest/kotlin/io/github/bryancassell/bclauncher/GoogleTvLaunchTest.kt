package io.github.bryancassell.bclauncher

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Installs and opens the app on a Google TV emulator, which Robolectric can't stand in for: the
 * install needs a device with leanback, and the TV app list's intent comes from the real system.
 */
@RunWith(AndroidJUnit4::class)
class GoogleTvLaunchTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tvAppListOpensTheHomeScreen() {
        val intent = checkNotNull(
            context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName),
        )

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        }
    }
}

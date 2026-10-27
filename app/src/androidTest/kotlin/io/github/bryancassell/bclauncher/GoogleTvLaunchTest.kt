package io.github.bryancassell.bclauncher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the app on a Google TV emulator, through the launch intent that Google TV's own package
 * manager returns for the TV app list, on the real framework rather than Robolectric's.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GoogleTvLaunchTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tvAppListOpensTheHomeScreenWithATileForEachTvApp() {
        val intent = checkNotNull(
            context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName),
        )
        val tvApps = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        // The overload that takes ResolveInfoFlags needs API 33, and the tests run from API 29.
        @Suppress("DEPRECATION")
        val activities = context.packageManager.queryIntentActivities(tvApps, 0)
        // The emulator's stock apps fit on the first screen, so every tile is composed.
        val labels = activities
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.loadLabel(context.packageManager).toString() }
        assertTrue(labels.isNotEmpty())

        ActivityScenario.launch<MainActivity>(intent).use {
            labels.forEach { label ->
                // A tile is described by its banner, or shows the app's name when it has none.
                composeRule.waitUntilExactlyOneExists(
                    hasClickAction() and (hasContentDescription(label) or hasText(label)),
                    timeoutMillis = 5_000,
                )
            }
        }
    }
}

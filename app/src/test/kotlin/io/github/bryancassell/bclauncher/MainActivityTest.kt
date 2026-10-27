package io.github.bryancassell.bclauncher

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bclauncher.ui.theme.DarkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// HiltTestApplication gives each test its own components, with FakePackageSourceModule in place of the real source.
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensToTheHomeScreen() {
        val appName = composeRule.activity.getString(R.string.app_name)

        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun windowBackgroundMatchesTheSurfaceComposeDrawsFirst() {
        val attributes = composeRule.activity.theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowBackground),
        )
        val windowBackground = attributes.getColor(0, 0)
        attributes.recycle()

        assertEquals(DarkColorScheme.surface.toArgb(), windowBackground)
    }
}

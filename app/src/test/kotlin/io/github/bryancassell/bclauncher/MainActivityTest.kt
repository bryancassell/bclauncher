package io.github.bryancassell.bclauncher

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.ui.theme.DarkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensToTheHomeScreen() {
        val appName = composeRule.activity.getString(R.string.app_name)

        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun windowBackgroundMatchesTheSurfaceComposeDrawsFirst() {
        val attributes = composeRule.activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val windowBackground = attributes.getColor(0, 0)
        attributes.recycle()

        assertEquals(DarkColorScheme.surface.toArgb(), windowBackground)
    }
}

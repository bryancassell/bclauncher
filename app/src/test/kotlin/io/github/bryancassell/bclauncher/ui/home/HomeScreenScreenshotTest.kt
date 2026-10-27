package io.github.bryancassell.bclauncher.ui.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Television1080p)
class HomeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen() {
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen()
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}

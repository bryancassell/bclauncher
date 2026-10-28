package io.github.bryancassell.bclauncher.ui.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// One SDK, because rendering can differ between Android versions and there's one baseline (#1).
@Config(sdk = [37])
class HomeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Seven apps with banners, so the grid wraps to a second row, then one without, which shows its icon and name.
    // The first tile is focused.
    @Test
    fun homeScreen() {
        composeRule.setContent {
            HomeScreenPreview()
        }

        composeRule.onRoot().captureRoboImage()
    }
}

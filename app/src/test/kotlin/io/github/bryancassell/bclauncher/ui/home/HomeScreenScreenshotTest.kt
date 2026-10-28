package io.github.bryancassell.bclauncher.ui.home

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme
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
        val context: Context = ApplicationProvider.getApplicationContext()
        val colors =
            listOf(
                0xFFC62828,
                0xFF1565C0,
                0xFF2E7D32,
                0xFF6A1B9A,
                0xFFEF6C00,
                0xFF00838F,
                0xFF4E342E,
            )
        val apps = colors.mapIndexed { index, color ->
            fakeApp("App $index").copy(banner = ColorDrawable(color.toInt()))
        } + fakeApp("No banner").copy(icon = context.getDrawable(R.mipmap.ic_launcher)!!)
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen(uiState = HomeUiState.Content(apps), onAppClick = {}, onRetry = {})
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}

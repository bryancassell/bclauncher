package io.github.bryancassell.bclauncher.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BcLauncherThemeTest {
    @Test
    fun windowBackgroundMatchesTheSurfaceComposeDrawsFirst() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_BcLauncher)

        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val windowBackground = attributes.getColor(0, 0)
        attributes.recycle()

        assertEquals(DarkColorScheme.surface.toArgb(), windowBackground)
    }
}

package io.github.bryancassell.bclauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Its surface color is also the window background in res/values/colors.xml (#1).
internal val DarkColorScheme = darkColorScheme()

@Composable
fun BcLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}

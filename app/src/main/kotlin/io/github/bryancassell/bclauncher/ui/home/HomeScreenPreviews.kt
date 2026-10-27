package io.github.bryancassell.bclauncher.ui.home

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme

// Devices.TV_1080p is 1920 x 1080 dp in ui-tooling-preview 1.12.1, twice what a 1080p TV reports (#1).
@Preview(device = "spec:width=960dp,height=540dp,dpi=320")
@Composable
private fun HomeScreenPreview() {
    val icon = LocalResources.current.getDrawable(R.mipmap.ic_launcher, null)
    val colors =
        listOf(0xFFC62828, 0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F, 0xFF4E342E)
    val apps = colors.mapIndexed { index, color ->
        InstalledApp(
            component = ComponentName(
                "com.example.app$index",
                "com.example.app$index.MainActivity",
            ),
            label = "App $index",
            banner = ColorDrawable(color.toInt()),
            icon = icon,
        )
    } + InstalledApp(
        component = ComponentName("com.example.nobanner", "com.example.nobanner.MainActivity"),
        label = "No banner",
        banner = null,
        icon = icon,
    )
    BcLauncherTheme {
        HomeScreen(uiState = HomeUiState.Content(apps), onAppClick = {})
    }
}

@Preview(device = "spec:width=960dp,height=540dp,dpi=320")
@Composable
private fun EmptyHomeScreenPreview() {
    BcLauncherTheme {
        HomeScreen(uiState = HomeUiState.Empty, onAppClick = {})
    }
}

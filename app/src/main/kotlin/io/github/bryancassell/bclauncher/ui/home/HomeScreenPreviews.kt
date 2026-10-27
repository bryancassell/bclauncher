package io.github.bryancassell.bclauncher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme

// Devices.TV_1080p is 1920 x 1080 dp in ui-tooling-preview 1.12.1, twice what a 1080p TV reports (#1).
@Preview(device = "spec:width=960dp,height=540dp,dpi=320")
@Composable
private fun HomeScreenPreview() {
    BcLauncherTheme {
        HomeScreen()
    }
}

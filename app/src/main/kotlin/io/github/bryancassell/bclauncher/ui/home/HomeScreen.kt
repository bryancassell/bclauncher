package io.github.bryancassell.bclauncher.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme

/** The launcher's home screen. For now it shows only the app's name; the app grid comes in #5. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(48.dp),
        )
    }
}

// Devices.TV_1080p is 1920 x 1080 dp in ui-tooling-preview 1.12.1, twice what a 1080p TV reports (#1).
@Preview(device = "spec:width=960dp,height=540dp,dpi=320")
@Composable
private fun HomeScreenPreview() {
    BcLauncherTheme {
        HomeScreen()
    }
}

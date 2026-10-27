package io.github.bryancassell.bclauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import io.github.bryancassell.bclauncher.ui.home.HomeScreen
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BcLauncherTheme {
                HomeScreen()
            }
        }
    }
}

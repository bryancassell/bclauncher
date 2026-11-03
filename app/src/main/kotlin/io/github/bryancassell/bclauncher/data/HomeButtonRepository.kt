package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bclauncher.HomeButtonService
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Whether the remote's Home button opens BC Launcher, which it does while [HomeButtonService] is on. */
class HomeButtonRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    accessibilitySource: AccessibilitySource,
) {
    private val service = ComponentName(context, HomeButtonService::class.java)

    /** Emits whether the user has turned on [HomeButtonService], then again each time that changes. */
    val isOn: Flow<Boolean> = accessibilitySource.enabledServices
        .map { service in it }
        .distinctUntilChanged()

    /**
     * Opens Accessibility settings, where the user turns [HomeButtonService] on or off. Not the service's own page:
     * ACTION_ACCESSIBILITY_DETAILS_SETTINGS opens nothing on the Google TV emulator (API 36) or a Google TV Streamer
     * (Android 14) (#10).
     */
    val settingsIntent: Intent
        get() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Tells [HomeButtonService] that the user is opening Google TV's home, so it doesn't open BC Launcher in its place.
     * Call it just before starting Google TV's home. It runs in another process, so this is a broadcast.
     */
    fun openingGoogleTvHome() {
        context.sendBroadcast(
            Intent(HomeButtonService.ACTION_OPENING_GOOGLE_TV_HOME).setPackage(context.packageName),
        )
    }
}

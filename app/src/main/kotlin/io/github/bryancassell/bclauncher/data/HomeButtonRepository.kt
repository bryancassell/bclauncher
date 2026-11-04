package io.github.bryancassell.bclauncher.data

import android.app.Activity
import android.content.BroadcastReceiver
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
     * Tells [HomeButtonService] that the user is opening Google TV's home, so it doesn't open BC Launcher in its place,
     * then calls [open] on the main thread to start it. The service runs in another process, so this is a broadcast.
     *
     * It's ordered, and [open] is its result receiver, so Google TV's home starts only once the service's receiver has
     * run. Otherwise the broadcast and Google TV's window event would reach the service in no set order, and BC
     * Launcher would open over Google TV's home whenever the broadcast came second. With the service off, no receiver
     * waits, and [open] runs at once. FLAG_RECEIVER_FOREGROUND keeps a busy background queue from holding it up.
     */
    fun openingGoogleTvHome(open: () -> Unit) {
        context.sendOrderedBroadcast(
            Intent(HomeButtonService.ACTION_OPENING_GOOGLE_TV_HOME)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = open()
            },
            null,
            Activity.RESULT_OK,
            null,
            null,
        )
    }
}

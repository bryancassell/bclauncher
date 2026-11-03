package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads the services from [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] and watches it for changes. Not
 * AccessibilityManager: its listener for a service turning on or off came in API 33, and minSdk is 29.
 */
class SettingsAccessibilitySource @Inject constructor(@ApplicationContext context: Context) :
    AccessibilitySource {
    private val contentResolver = context.contentResolver

    override val enabledServices: Flow<Set<ComponentName>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(readEnabledServices())
            }
        }
        val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        contentResolver.registerContentObserver(uri, false, observer)
        trySend(readEnabledServices())
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }

    // Flattened components separated by colons, in either the long or the short form, or null when none is on.
    private fun readEnabledServices(): Set<ComponentName> =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNullTo(HashSet(), ComponentName::unflattenFromString)
}

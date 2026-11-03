package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Reads the services from [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] and watches it for changes. Not
 * AccessibilityManager: its listener for a service turning on or off came in API 33, and minSdk is 29.
 */
class SettingsAccessibilitySource @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccessibilitySource {
    private val contentResolver = context.contentResolver

    /**
     * Reading the setting is a call to the settings provider, so it runs on the IO dispatcher. With no Handler, the
     * observer is told of changes on a binder thread. Changes that arrive during a read are merged into one more read.
     */
    override val enabledServices: Flow<Set<ComponentName>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        contentResolver.registerContentObserver(uri, false, observer)
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }
        .conflate()
        .map { readEnabledServices() }
        .flowOn(ioDispatcher)

    // Flattened components separated by colons, in either the long or the short form, or null when none is on.
    private fun readEnabledServices(): Set<ComponentName> =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNullTo(HashSet(), ComponentName::unflattenFromString)
}

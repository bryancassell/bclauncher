package io.github.bryancassell.bclauncher.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Tells whether the TV has restarted, by the count of starts Android keeps in [Settings.Global.BOOT_COUNT]. */
class RestartRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val contentResolver = context.contentResolver

    /**
     * Whether the TV has restarted since the last call, and remembers this start either way. The first call ever
     * answers false, since there's no start to compare with.
     */
    suspend fun hasRestartedSinceLastCall(): Boolean {
        val start = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
        var restarted = false
        dataStore.edit { preferences ->
            val last = preferences[LAST_START]
            restarted = last != null && last != start
            preferences[LAST_START] = start
        }
        return restarted
    }

    private companion object {
        val LAST_START = intPreferencesKey("last_start")
    }
}

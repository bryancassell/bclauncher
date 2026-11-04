package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "InstalledAppsRepository"

/**
 * The apps the home screen lists, in the order the user put them in, kept current as apps are installed, updated or
 * removed.
 */
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageSource: PackageSource,
    private val orderStore: DataStore<AppOrder>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    // Cancelling a collection doesn't stop its load, since PackageManager's calls block. A load waits here for one a
    // cancelled collection left running, so restarting the collection, as the home screen's reloads do, doesn't run
    // two loads of every label and image at once (#5).
    private val loading = Mutex()

    /**
     * Every app with a TV launch activity except this one (PRD.md, Design decisions), in the saved order, emitted again
     * after each package or language change and each time the order is saved. Updating an app sends several changes in
     * a row, so changes that arrive during a load are merged into one more load.
     */
    val apps: Flow<List<InstalledApp>> = packageSource.changes
        .conflate()
        .map { loading.withLock { loadApps() } }
        .combine(savedOrder()) { apps, order -> order.arrange(apps) }
        .flowOn(ioDispatcher)

    /** Saves [apps] as the order to show them in, first tile first. */
    suspend fun saveOrder(apps: List<ComponentName>) {
        orderStore.updateData { AppOrder(apps.map { it.flattenToString() }) }
    }

    // DataStore's data throws an IOException when it can't read the file. The apps then show alphabetically rather than
    // not at all. A file it reads but can't parse is replaced instead, by the handler DataStoreModule gives it.
    private fun savedOrder(): Flow<AppOrder> = orderStore.data.catch { error ->
        if (error !is IOException) throw error
        Log.e(TAG, "Couldn't read the app order", error)
        emit(AppOrder())
    }

    /** The apps by label, the order [AppOrder.arrange] starts from. */
    private fun loadApps(): List<InstalledApp> {
        // Collator orders labels by the user's language, so "apple" sorts before "Banana". Apps with the same label
        // are ordered by component, so they don't swap places when PackageManager returns them in another order.
        val order = compareBy(Collator.getInstance()) { app: InstalledApp -> app.label }
            .thenBy { it.component.flattenToString() }
        return packageSource.tvLauncherActivities()
            .filter { it.component.packageName != context.packageName }
            .sortedWith(order)
    }
}

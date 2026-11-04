package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     *
     * When DataStore can't read the order's file at all, this throws, as a failed load does, so the screen offers to
     * try again. Showing the apps alphabetically instead would let the user move one and save over the order they had
     * stored (#8). A file DataStore reads but can't parse is replaced, by the handler DataStoreModule gives it.
     */
    val apps: Flow<List<InstalledApp>> = packageSource.changes
        .conflate()
        .map { loading.withLock { loadApps() } }
        .combine(orderStore.data) { apps, order -> order.arrange(apps) }
        .flowOn(ioDispatcher)

    /** Saves [components] as the order to show the apps in, first tile first. */
    suspend fun saveOrder(components: List<ComponentName>) {
        orderStore.updateData { AppOrder(components.map { it.flattenToString() }) }
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

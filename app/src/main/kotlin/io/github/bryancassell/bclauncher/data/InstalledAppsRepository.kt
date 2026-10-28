package io.github.bryancassell.bclauncher.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The apps the home screen lists, kept current as apps are installed, updated or removed. */
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageSource: PackageSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    // Cancelling a collection doesn't stop its load, since PackageManager's calls block. A load waits here for one a
    // cancelled collection left running, so restarting the collection, as the home screen's reloads do, doesn't run
    // two loads of every label and image at once (#5).
    private val loading = Mutex()

    /**
     * Every app with a TV launch activity except this one, by label, emitted again after each package or language
     * change (PRD.md, Design decisions). Updating an app sends several changes in a row, so changes that arrive
     * during a load are merged into one more load.
     */
    val apps: Flow<List<InstalledApp>> = packageSource.changes
        .conflate()
        .map { loading.withLock { loadApps() } }
        .flowOn(ioDispatcher)

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

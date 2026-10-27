package io.github.bryancassell.bclauncher.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.text.Collator
import javax.inject.Inject

/** The apps the home screen lists, kept current as apps are installed, updated or removed. */
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageSource: PackageSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Every app with a TV launch activity except this one, by label, emitted again after each package change
     * (PRD.md, Design decisions). Updating an app sends several changes in a row, so changes that arrive during a
     * load are merged into one more load.
     */
    val apps: Flow<List<InstalledApp>> = packageSource.packageChanges
        .onStart { emit(Unit) }
        .conflate()
        .map { loadApps() }
        .flowOn(ioDispatcher)

    private fun loadApps(): List<InstalledApp> {
        // Collator orders labels by the user's language, so "apple" sorts before "Banana".
        val collator = Collator.getInstance()
        return packageSource.tvLauncherActivities()
            .filter { it.component.packageName != context.packageName }
            .sortedWith(compareBy(collator) { it.label })
    }
}

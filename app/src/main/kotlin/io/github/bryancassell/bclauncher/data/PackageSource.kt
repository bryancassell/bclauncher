package io.github.bryancassell.bclauncher.data

import kotlinx.coroutines.flow.Flow

/** The installed apps as the platform reports them. Tests replace it with a fake. */
interface PackageSource {
    /** Every activity that opens from a TV's app list, in no particular order. It reads PackageManager, so it blocks. */
    fun tvLauncherActivities(): List<InstalledApp>

    /**
     * Emits once as soon as it's listening, then each time [tvLauncherActivities] may have changed: when an app is
     * installed, updated, removed, enabled or disabled, or the language changes. Emitting first only once it's
     * listening means a change during the first load still causes another.
     */
    val changes: Flow<Unit>
}

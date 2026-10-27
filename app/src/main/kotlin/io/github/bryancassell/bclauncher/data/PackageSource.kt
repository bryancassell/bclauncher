package io.github.bryancassell.bclauncher.data

import kotlinx.coroutines.flow.Flow

/** The installed apps as the platform reports them. Tests replace it with a fake. */
interface PackageSource {
    /** Every activity that opens from a TV's app list, in no particular order. It reads PackageManager, so it blocks. */
    fun tvLauncherActivities(): List<InstalledApp>

    /** Emits each time an app is installed, updated, removed, enabled or disabled. */
    val packageChanges: Flow<Unit>
}

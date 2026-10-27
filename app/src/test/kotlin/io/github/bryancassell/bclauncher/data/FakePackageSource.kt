package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A [PackageSource] whose apps the test sets. In a Hilt test, the app gets this same instance. */
@Singleton
class FakePackageSource @Inject constructor() : PackageSource {
    private var activities: List<InstalledApp> = emptyList()

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    override fun tvLauncherActivities(): List<InstalledApp> = activities

    override val packageChanges: Flow<Unit> = changes

    /** Replaces the installed apps and reports a package change, as an install or removal would. */
    fun setApps(vararg apps: InstalledApp) {
        activities = apps.toList()
        changes.tryEmit(Unit)
    }
}

/** An app whose package and activity names come from its label. */
fun fakeApp(label: String, packageName: String = "com.example.${label.lowercase()}") = InstalledApp(
    component = ComponentName(packageName, "$packageName.MainActivity"),
    label = label,
    banner = null,
    icon = ColorDrawable(),
)

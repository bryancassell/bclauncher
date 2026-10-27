package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import javax.inject.Inject
import javax.inject.Singleton

/** A [PackageSource] whose apps the test sets. In a Hilt test, the app gets this same instance. */
@Singleton
class FakePackageSource @Inject constructor() : PackageSource {
    // The test thread writes it, and the repository may read it on another thread.
    @Volatile
    private var activities: List<InstalledApp> = emptyList()

    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    /** How many times the apps were read. */
    var loads = 0
        private set

    override fun tvLauncherActivities(): List<InstalledApp> {
        loads++
        return activities
    }

    override val changes: Flow<Unit> = changeEvents.onSubscription { emit(Unit) }

    /** Replaces the installed apps and reports a change, as an install or removal would. */
    fun setApps(vararg apps: InstalledApp) {
        activities = apps.toList()
        changeEvents.tryEmit(Unit)
    }
}

/** An app whose package and activity names come from its label. */
fun fakeApp(label: String, packageName: String = "com.example.${label.lowercase()}") = InstalledApp(
    component = ComponentName(packageName, "$packageName.MainActivity"),
    label = label,
    banner = null,
    icon = ColorDrawable(),
)

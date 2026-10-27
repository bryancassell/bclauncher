package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** A [PackageSource] whose apps the test sets. In a Hilt test, the app gets this same instance. */
@Singleton
class FakePackageSource @Inject constructor() : PackageSource {
    // The test thread writes these, and the repository may read them on another thread.
    @Volatile
    private var activities: List<InstalledApp> = emptyList()

    @Volatile
    private var nextLoadAction: (() -> Unit)? = null

    private val loadCount = AtomicInteger()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** How many times the apps were read. */
    val loads: Int get() = loadCount.get()

    override fun tvLauncherActivities(): List<InstalledApp> {
        loadCount.incrementAndGet()
        val apps = activities
        nextLoadAction?.also { nextLoadAction = null }?.invoke()
        return apps
    }

    // A callbackFlow, like PackageManagerSource's, so a change goes straight into the channel the repository reads.
    override val changes: Flow<Unit> = callbackFlow {
        val listener: () -> Unit = { trySend(Unit) }
        listeners += listener
        trySend(Unit)
        awaitClose { listeners -= listener }
    }

    /** Replaces the installed apps and reports a change, as an install or removal would. */
    fun setApps(vararg apps: InstalledApp) {
        activities = apps.toList()
        listeners.forEach { it() }
    }

    /** Runs [action] inside the next load, after it has read the apps, as if it happened while the load ran. */
    fun duringNextLoad(action: () -> Unit) {
        nextLoadAction = action
    }
}

/** An app whose package and activity names come from its label. */
fun fakeApp(label: String, packageName: String = "com.example.${label.lowercase()}") = InstalledApp(
    component = ComponentName(packageName, "$packageName.MainActivity"),
    label = label,
    banner = null,
    icon = ColorDrawable(),
)

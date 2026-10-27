package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Reads the apps from PackageManager. The manifest's `<queries>` makes their packages visible from API 30,
 * without `QUERY_ALL_PACKAGES`.
 */
class PackageManagerSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : PackageSource {
    private val packageManager = context.packageManager

    override fun tvLauncherActivities(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return queryActivities(intent).map { resolveInfo ->
            val activity = resolveInfo.activityInfo
            InstalledApp(
                component = ComponentName(activity.packageName, activity.name),
                label = resolveInfo.loadLabel(packageManager).toString(),
                // The activity's banner, else the application's, else null.
                banner = activity.loadBanner(packageManager),
                icon = resolveInfo.loadIcon(packageManager),
            )
        }
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

    override val packageChanges: Flow<Unit> = callbackFlow {
        val callback = PackageChangeCallback { trySend(Unit) }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        // Without a Handler, LauncherApps makes one on the calling thread, which throws on a thread with no Looper,
        // such as the Dispatchers.IO thread this flow is collected on.
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
}

/**
 * Calls [onChange] for every package change LauncherApps reports.
 *
 * LauncherApps.Callback, as Android's own launcher uses, rather than package broadcasts: one registration covers
 * installs, updates, removals, enabling and disabling, and apps on storage that comes and goes. It still reports a
 * package once it's uninstalled and no longer visible (LauncherAppsService.isPackageVisibleToListener, AOSP main).
 */
internal class PackageChangeCallback(private val onChange: () -> Unit) : LauncherApps.Callback() {
    override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()

    override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()

    override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()

    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
        onChange()

    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
        onChange()
}

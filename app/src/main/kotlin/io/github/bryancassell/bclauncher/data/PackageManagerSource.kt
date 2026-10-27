package io.github.bryancassell.bclauncher.data

import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads the apps from PackageManager. The manifest's `<queries>` makes their packages visible from API 30,
 * without `QUERY_ALL_PACKAGES`.
 */
class PackageManagerSource @Inject constructor(@ApplicationContext private val context: Context) :
    PackageSource {
    private val packageManager = context.packageManager

    override fun tvLauncherActivities(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return queryActivities(intent)
            .filter { canLaunch(it.activityInfo) }
            .map { resolveInfo ->
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

    /**
     * Whether this app may start [activity]. Launching one it may not start would throw a SecurityException, but the
     * query still returns:
     * - Activities that aren't exported, before Android 14. Android 14 leaves them out of implicit intent resolution
     *   (SaferIntentUtils.filterNonExportedComponents, AOSP main in October 2026, #4).
     * - Activities that need a permission this app doesn't hold. [ActivityInfo.permission] is the application's
     *   permission when the activity doesn't set its own.
     */
    private fun canLaunch(activity: ActivityInfo): Boolean {
        if (!activity.exported) return false
        val permission = activity.permission ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

    override val changes: Flow<Unit> = callbackFlow {
        val callback = PackageChangeCallback { trySend(Unit) }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        // Without a Handler, LauncherApps makes one on the calling thread, which throws on a thread with no Looper,
        // such as the Dispatchers.IO thread this flow is collected on (LauncherApps.addCallbackLocked, AOSP main in
        // October 2026, #4).
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val localeCallback = LocaleChangeCallback(context.resources.configuration) { trySend(Unit) }
        context.registerComponentCallbacks(localeCallback)
        trySend(Unit)
        awaitClose {
            context.unregisterComponentCallbacks(localeCallback)
            launcherApps.unregisterCallback(callback)
        }
    }
}

/**
 * Calls [onChange] when the language changes. Labels and banners come in the TV's language, and a language change
 * recreates the activity but not the ViewModel collecting the list, so without a reload the old names would stay.
 *
 * Not ACTION_LOCALE_CHANGED: on the Google TV emulator (API 36) that broadcast arrived 50 to 100 ms before this
 * process's resources switched language, so a reload it started read the old labels. The application's
 * onConfigurationChanged comes after the resources are updated (#4).
 */
internal class LocaleChangeCallback(
    configuration: Configuration,
    private val onChange: () -> Unit,
) : ComponentCallbacks {
    private var locales = configuration.locales

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.locales != locales) {
            locales = newConfig.locales
            onChange()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = Unit
}

/**
 * Calls [onChange] for every package change LauncherApps reports.
 *
 * LauncherApps.Callback, as Android's own launcher uses, rather than package broadcasts: one registration covers
 * installs, updates, removals, enabling and disabling, and apps on storage that comes and goes. It still reports a
 * package once it's uninstalled and no longer visible (LauncherAppsService.isPackageVisibleToListener, AOSP main in
 * October 2026, #4).
 */
internal class PackageChangeCallback(private val onChange: () -> Unit) : LauncherApps.Callback() {
    override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()

    override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()

    override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()

    override fun onPackagesAvailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = onChange()

    override fun onPackagesUnavailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = onChange()
}

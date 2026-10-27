package io.github.bryancassell.bclauncher.data

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.LocaleList
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PackageManagerSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = shadowOf(context.packageManager)
    private val source = PackageManagerSource(context)

    /** Installs a package with one activity that opens from the given launcher category. */
    private fun install(
        packageName: String,
        category: String = Intent.CATEGORY_LEANBACK_LAUNCHER,
        label: String = packageName,
        applicationBanner: Int = 0,
        activityBanner: Int = 0,
        exported: Boolean = true,
        permission: String? = null,
    ) {
        val application = ApplicationInfo().apply {
            this.packageName = packageName
            banner = applicationBanner
        }
        packageManager.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = application
            },
        )
        val activity = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
            nonLocalizedLabel = label
            banner = activityBanner
            this.exported = exported
            this.permission = permission
            applicationInfo = application
        }
        packageManager.addOrUpdateActivity(activity)
        packageManager.addIntentFilterForActivity(
            ComponentName(packageName, activity.name),
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
        )
    }

    private fun listedApp(packageName: String) =
        source.tvLauncherActivities().single { it.component.packageName == packageName }

    /** Collects [PackageSource.changes] for the rest of the test, and returns how many have arrived so far. */
    private fun TestScope.countChanges(): () -> Int {
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.changes.collect { changes++ }
        }
        return { changes }
    }

    @Test
    fun listsActivitiesThatOpenFromTheTvAppList() {
        install("com.example.tv")
        install("com.example.phone", category = Intent.CATEGORY_LAUNCHER)

        val packages = source.tvLauncherActivities().map { it.component.packageName }

        // This app's own MainActivity is listed too. The repository leaves it out.
        assertEquals(setOf("com.example.tv", context.packageName), packages.toSet())
    }

    @Test
    fun leavesOutActivitiesThatAreNotExported() {
        install("com.example.tv")
        install("com.example.private", exported = false)

        val packages = source.tvLauncherActivities().map { it.component.packageName }

        assertEquals(setOf("com.example.tv", context.packageName), packages.toSet())
    }

    @Test
    fun listsActivitiesThatNeedAPermissionOnlyWhenThisAppHoldsIt() {
        install("com.example.held", permission = HELD_PERMISSION)
        install("com.example.missing", permission = "com.example.permission.PRIVATE")
        shadowOf(context as Application).grantPermissions(HELD_PERMISSION)

        val packages = source.tvLauncherActivities().map { it.component.packageName }

        assertEquals(setOf("com.example.held", context.packageName), packages.toSet())
    }

    @Test
    fun readsTheLabelAndTheApplicationsBanner() {
        val banner = ColorDrawable()
        packageManager.addDrawableResolution("com.example.tv", APPLICATION_BANNER, banner)
        install("com.example.tv", label = "Example TV", applicationBanner = APPLICATION_BANNER)

        val app = listedApp("com.example.tv")

        assertEquals(ComponentName("com.example.tv", "com.example.tv.MainActivity"), app.component)
        assertEquals("Example TV", app.label)
        assertSame(banner, app.banner)
    }

    @Test
    fun prefersTheActivitysBannerToTheApplications() {
        val activityBanner = ColorDrawable()
        packageManager.addDrawableResolution("com.example.tv", APPLICATION_BANNER, ColorDrawable())
        packageManager.addDrawableResolution("com.example.tv", ACTIVITY_BANNER, activityBanner)
        install(
            "com.example.tv",
            applicationBanner = APPLICATION_BANNER,
            activityBanner = ACTIVITY_BANNER,
        )

        assertSame(activityBanner, listedApp("com.example.tv").banner)
    }

    @Test
    fun bannerIsNullWhenTheAppHasNone() {
        install("com.example.tv")

        assertNull(listedApp("com.example.tv").banner)
    }

    @Test
    fun emitsOnceListeningThenWhenAPackageIsAddedOrRemoved() = runTest {
        val changes = countChanges()
        assertEquals(1, changes())
        val launcherApps = shadowOf(context.getSystemService(LauncherApps::class.java))

        launcherApps.notifyPackageAdded("com.example.tv")
        launcherApps.notifyPackageRemoved("com.example.tv")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(3, changes())
    }

    @Test
    fun emitsWhenTheLanguageChangesButNotForOtherConfigurationChanges() = runTest {
        val changes = countChanges()
        val application = context as Application
        val german = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("de-DE"))
        }

        application.onConfigurationChanged(german)
        application.onConfigurationChanged(Configuration(german).apply { fontScale = 1.3f })

        assertEquals(2, changes())
    }

    @Test
    fun startsListeningOnAThreadWithNoLooper() = runTest {
        // The repository collects this on Dispatchers.IO, whose threads have no Looper.
        val first = withContext(Dispatchers.IO) { source.changes.first() }

        assertEquals(Unit, first)
    }

    private companion object {
        const val APPLICATION_BANNER = 0x7f010001
        const val ACTIVITY_BANNER = 0x7f010002
        const val HELD_PERMISSION = "com.example.permission.HELD"
    }
}

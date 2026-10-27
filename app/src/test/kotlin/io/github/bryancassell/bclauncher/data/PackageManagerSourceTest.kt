package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PackageManagerSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = shadowOf(context.packageManager)
    private val source = PackageManagerSource(context)

    /** Installs a package with one activity that opens from the given launcher category. */
    private fun install(packageName: String, category: String, label: String = packageName, banner: Int = 0) {
        val application = ApplicationInfo().apply {
            this.packageName = packageName
            this.banner = banner
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
            applicationInfo = application
        }
        packageManager.addOrUpdateActivity(activity)
        packageManager.addIntentFilterForActivity(
            ComponentName(packageName, activity.name),
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
        )
    }

    @Test
    fun listsActivitiesThatOpenFromTheTvAppList() {
        install("com.example.tv", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("com.example.phone", Intent.CATEGORY_LAUNCHER)

        val packages = source.tvLauncherActivities().map { it.component.packageName }

        // This app's own MainActivity is listed too. The repository leaves it out.
        assertEquals(setOf("com.example.tv", context.packageName), packages.toSet())
    }

    @Test
    fun readsTheLabelAndTheApplicationsBanner() {
        val banner = ColorDrawable()
        packageManager.addDrawableResolution("com.example.tv", BANNER_ID, banner)
        install("com.example.tv", Intent.CATEGORY_LEANBACK_LAUNCHER, label = "Example TV", banner = BANNER_ID)

        val app = source.tvLauncherActivities().single { it.component.packageName == "com.example.tv" }

        assertEquals(ComponentName("com.example.tv", "com.example.tv.MainActivity"), app.component)
        assertEquals("Example TV", app.label)
        assertSame(banner, app.banner)
    }

    @Test
    fun bannerIsNullWhenTheAppHasNone() {
        install("com.example.tv", Intent.CATEGORY_LEANBACK_LAUNCHER)

        val app = source.tvLauncherActivities().single { it.component.packageName == "com.example.tv" }

        assertNull(app.banner)
    }

    @Test
    fun emitsWhenAPackageIsAddedOrRemoved() = runTest {
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.packageChanges.collect { changes++ }
        }
        val launcherApps = shadowOf(context.getSystemService(LauncherApps::class.java))

        launcherApps.notifyPackageAdded("com.example.tv")
        launcherApps.notifyPackageRemoved("com.example.tv")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, changes)
    }

    private companion object {
        const val BANNER_ID = 0x7f010001
    }
}

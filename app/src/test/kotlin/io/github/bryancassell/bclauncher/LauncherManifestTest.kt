package io.github.bryancassell.bclauncher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tvAppListOpensMainActivity() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setPackage(context.packageName)

        val activities = context.packageManager.queryIntentActivities(intent, 0)

        assertEquals(listOf(MainActivity::class.java.name), activities.map { it.activityInfo.name })
    }

    @Test
    fun appHasABannerAtTheSizeTvHomeScreensUse() {
        val banner = context.packageManager.getApplicationBanner(context.packageName)

        // Android TV asks for a 320 x 180 px banner at xhdpi, the density robolectric.properties sets (#1).
        assertNotNull(banner)
        assertEquals(320 to 180, banner!!.intrinsicWidth to banner.intrinsicHeight)
    }

    @Test
    fun requiresLeanbackButNotATouchscreen() {
        val features = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
            .reqFeatures
            .orEmpty()
            .associate { it.name to (it.flags and FeatureInfo.FLAG_REQUIRED != 0) }

        assertEquals(
            mapOf(
                PackageManager.FEATURE_LEANBACK to true,
                PackageManager.FEATURE_TOUCHSCREEN to false,
            ),
            features,
        )
    }

    @Test
    fun requestsThePermissionToAskToUninstallApps() {
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()

        assertTrue(Manifest.permission.REQUEST_DELETE_PACKAGES in permissions)
    }

    @Test
    fun homeReturnsToTheOneHomeScreen() {
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activity.launchMode)
    }

    @Test
    fun homeButtonServiceIsAnAccessibilityServiceOnlyTheSystemCanBindInItsOwnProcess() {
        val intent = Intent(AccessibilityService.SERVICE_INTERFACE).setPackage(context.packageName)

        val service = context.packageManager.queryIntentServices(
            intent,
            PackageManager.GET_META_DATA,
        ).single()

        assertEquals(HomeButtonService::class.java.name, service.serviceInfo.name)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, service.serviceInfo.permission)
        assertEquals("${context.packageName}:home_button", service.serviceInfo.processName)
        assertEquals(
            R.xml.home_button_service,
            service.serviceInfo.metaData.getInt(AccessibilityService.SERVICE_META_DATA),
        )
    }
}

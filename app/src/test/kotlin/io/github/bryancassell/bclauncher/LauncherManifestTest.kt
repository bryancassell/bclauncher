package io.github.bryancassell.bclauncher

import android.content.Context
import android.content.Intent
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}

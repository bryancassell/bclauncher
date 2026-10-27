package io.github.bryancassell.bclauncher.data

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric's LauncherApps can only report added and removed packages, so this calls each callback directly. */
@RunWith(AndroidJUnit4::class)
class PackageChangeCallbackTest {
    private var changes = 0
    private val callback = PackageChangeCallback { changes++ }
    private val user = Process.myUserHandle()

    @Test
    fun reportsEveryKindOfPackageChange() {
        callback.onPackageAdded("com.example.tv", user)
        callback.onPackageRemoved("com.example.tv", user)
        callback.onPackageChanged("com.example.tv", user)
        callback.onPackagesAvailable(arrayOf("com.example.tv"), user, false)
        callback.onPackagesUnavailable(arrayOf("com.example.tv"), user, false)

        assertEquals(5, changes)
    }
}

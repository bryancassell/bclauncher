package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(AndroidJUnit4::class)
class HomeButtonServiceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val controller: ServiceController<HomeButtonService> =
        Robolectric.buildService(HomeButtonService::class.java)
    private val service: HomeButtonService by lazy { controller.create().get() }

    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private val googleTvHome = ComponentName(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.apps.tv.launcherx.home.HomeActivity",
    )

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun send(action: Int, keyCode: Int = KeyEvent.KEYCODE_HOME) =
        service.onKeyEvent(KeyEvent(action, keyCode))

    private fun sendCancelledUp(): Boolean {
        val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 0)
        return service.onKeyEvent(KeyEvent.changeFlags(up, KeyEvent.FLAG_CANCELED))
    }

    private val startedActivity: Intent? get() = shadowOf(application).nextStartedActivity

    private val globalActions: List<Int> get() = shadowOf(service).globalActionsPerformed

    private fun assertOpenedBcLauncher(started: Intent?) {
        assertEquals(ComponentName(application, MainActivity::class.java), started?.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started?.flags)
    }

    // Google TV's launcher is the system's Home app, as on a Google TV device.
    private fun installGoogleTvHome() {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        shadowOf(application.packageManager).apply {
            addActivityIfNotPresent(googleTvHome)
            addIntentFilterForActivity(googleTvHome, filter)
        }
    }

    // The window in front as the service connects, or none.
    private fun connectWithInFront(packageName: String?) {
        if (packageName != null) {
            @Suppress("DEPRECATION")
            val root = AccessibilityNodeInfo.obtain().apply { this.packageName = packageName }
            shadowOf(service).setRootInActiveWindow(root)
        }
        service.onServiceConnected()
    }

    private fun windowComesToTheFront(packageName: String) {
        @Suppress("DEPRECATION")
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = packageName
        service.onAccessibilityEvent(event)
    }

    @Test
    fun pressingHomeOpensBcLauncher() {
        assertTrue(send(KeyEvent.ACTION_DOWN))
        idleFor(longPressTimeout - 1)
        assertTrue(send(KeyEvent.ACTION_UP))

        assertOpenedBcLauncher(startedActivity)
        idleFor(longPressTimeout)
        assertEquals(emptyList<Int>(), globalActions)
    }

    @Test
    fun holdingHomeOpensGoogleTvsPanelWhileItIsStillHeld() {
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)

        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), globalActions)

        send(KeyEvent.ACTION_UP)
        assertNull(startedActivity)
    }

    @Test
    fun aPressAfterALongPressIsTimedOnItsOwn() {
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)
        send(KeyEvent.ACTION_UP)

        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_UP)

        assertOpenedBcLauncher(startedActivity)
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), globalActions)
    }

    @Test
    fun aCancelledPressOpensNothing() {
        send(KeyEvent.ACTION_DOWN)

        // As when the remote disconnects while Home is held.
        assertTrue(sendCancelledUp())
        idleFor(longPressTimeout)

        assertNull(startedActivity)
        assertEquals(emptyList<Int>(), globalActions)
    }

    @Test
    fun aSecondDownStartsTheLongPressTimingAgain() {
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout - 100)

        // A DOWN with no UP before it, as when the UP was lost.
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout - 1)
        assertEquals(emptyList<Int>(), globalActions)

        idleFor(1)
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), globalActions)
    }

    @Test
    fun otherKeysGoToTheAppOnScreen() {
        assertFalse(send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        assertNull(startedActivity)
    }

    @Test
    fun turningTheServiceOffWhileHomeIsHeldOpensNothing() {
        send(KeyEvent.ACTION_DOWN)

        controller.destroy()
        idleFor(longPressTimeout)

        assertEquals(emptyList<Int>(), globalActions)
    }

    @Test
    fun opensBcLauncherInPlaceOfGoogleTvsHomeWhenTheTvStarts() {
        installGoogleTvHome()

        connectWithInFront(googleTvHome.packageName)

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun leavesTheAppInFrontWhenItConnects() {
        installGoogleTvHome()

        // As when the user has just turned the service on, or the TV started in an HDMI input.
        connectWithInFront("com.android.tv.settings")
        windowComesToTheFront(googleTvHome.packageName)

        assertNull(startedActivity)
    }

    @Test
    fun opensBcLauncherWhenGoogleTvsHomeIsTheFirstWindowAfterItConnects() {
        installGoogleTvHome()

        connectWithInFront(null)
        assertNull(startedActivity)
        windowComesToTheFront(googleTvHome.packageName)

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun leavesTheFirstAppAfterItConnectsWhenItIsNotGoogleTvsHome() {
        installGoogleTvHome()

        connectWithInFront(null)
        windowComesToTheFront("com.google.android.youtube.tv")
        windowComesToTheFront(googleTvHome.packageName)

        assertNull(startedActivity)
    }

    @Test
    fun opensNothingOnConnectingWithoutAHomeApp() {
        connectWithInFront(googleTvHome.packageName)

        assertNull(startedActivity)
    }

    @Test
    fun windowsAfterTheFirstOneOpenNothing() {
        installGoogleTvHome()
        connectWithInFront("com.google.android.youtube.tv")

        // The user goes back to Google TV's home later, as from an entry for it.
        windowComesToTheFront(googleTvHome.packageName)

        assertNull(startedActivity)
    }
}

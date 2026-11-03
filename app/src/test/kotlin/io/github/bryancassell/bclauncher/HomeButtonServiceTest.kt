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

    // How long after handing Home to Android BC Launcher opens.
    private val handOffDelay = 100L

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

    private fun pressHome() {
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_UP)
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

    private fun setInFront(packageName: String) {
        @Suppress("DEPRECATION")
        val root = AccessibilityNodeInfo.obtain().apply { this.packageName = packageName }
        shadowOf(service).setRootInActiveWindow(root)
    }

    // The window in front as the service connects, or none.
    private fun connectWithInFront(packageName: String?) {
        if (packageName != null) setInFront(packageName)
        service.onServiceConnected()
    }

    // As the system does when the screensaver starts or stops. Sent from the service, so it's running to receive it.
    private fun screensaver(action: String) {
        service.sendBroadcast(Intent(action))
        idleFor(0)
    }

    private fun windowEventFrom(packageName: String, className: String? = null) {
        @Suppress("DEPRECATION")
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = packageName
        event.className = className
        service.onAccessibilityEvent(event)
    }

    private fun windowComesToTheFront(packageName: String, className: String? = null) {
        setInFront(packageName)
        windowEventFrom(packageName, className)
    }

    private fun googleTvsHomeComesToTheFront() {
        windowComesToTheFront(googleTvHome.packageName, googleTvHome.className)
    }

    // The service connected with BC Launcher in front, then the user pressed Home and BC Launcher opened.
    private fun handOffWithGoogleTvHome() {
        installGoogleTvHome()
        connectWithInFront(application.packageName)
        pressHome()
        idleFor(handOffDelay)
        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun pressingHomeHandsItToAndroidThenOpensBcLauncher() {
        assertTrue(send(KeyEvent.ACTION_DOWN))
        idleFor(longPressTimeout - 1)
        assertTrue(send(KeyEvent.ACTION_UP))

        // Android turns the TV on, switches its input and starts Google TV's home.
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_HOME), globalActions)
        idleFor(handOffDelay - 1)
        assertNull(startedActivity)

        idleFor(1)
        assertOpenedBcLauncher(startedActivity)
        idleFor(longPressTimeout)
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_HOME), globalActions)
    }

    @Test
    fun holdingHomeOpensGoogleTvsPanelWhileItIsStillHeld() {
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)

        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), globalActions)

        send(KeyEvent.ACTION_UP)
        idleFor(handOffDelay)
        assertNull(startedActivity)
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), globalActions)
    }

    @Test
    fun aPressAfterALongPressIsTimedOnItsOwn() {
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)
        send(KeyEvent.ACTION_UP)

        pressHome()
        idleFor(handOffDelay)

        assertOpenedBcLauncher(startedActivity)
        assertEquals(
            listOf(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                AccessibilityService.GLOBAL_ACTION_HOME,
            ),
            globalActions,
        )
    }

    @Test
    fun aCancelledPressDoesNothing() {
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
        assertEquals(emptyList<Int>(), globalActions)
    }

    @Test
    fun turningTheServiceOffWhileHomeIsHeldOpensNothing() {
        send(KeyEvent.ACTION_DOWN)

        controller.destroy()
        idleFor(longPressTimeout)

        assertEquals(emptyList<Int>(), globalActions)
    }

    @Test
    fun turningTheServiceOffRightAfterAHandOffOpensNothing() {
        pressHome()

        controller.destroy()
        idleFor(handOffDelay)

        assertNull(startedActivity)
    }

    @Test
    fun bcLauncherOpensAgainWhenGoogleTvsHomeEndsUpInFrontAfterAHandOff() {
        handOffWithGoogleTvHome()

        // As on a TV where Google TV's home starts later than BC Launcher.
        googleTvsHomeComesToTheFront()

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun googleTvsPanelAfterAHandOffOpensNothing() {
        handOffWithGoogleTvHome()

        // Google TV's panel is in the same package as its home.
        windowComesToTheFront(
            googleTvHome.packageName,
            "com.google.android.apps.tv.launcherx.dashboard.DashboardActivity",
        )

        assertNull(startedActivity)
    }

    @Test
    fun theNextPressEndsTheWaitForGoogleTvsHome() {
        handOffWithGoogleTvHome()

        send(KeyEvent.ACTION_DOWN)
        googleTvsHomeComesToTheFront()

        assertNull(startedActivity)
    }

    @Test
    fun googleTvsHomeBehindBcLauncherAfterAHandOffOpensNothing() {
        handOffWithGoogleTvHome()

        // Google TV's home sends window events while BC Launcher covers it.
        windowEventFrom(googleTvHome.packageName, googleTvHome.className)

        assertNull(startedActivity)
    }

    @Test
    fun googleTvsHomeLongAfterAHandOffOpensNothing() {
        handOffWithGoogleTvHome()
        idleFor(1_500)

        // The user opens Google TV's home, as from an entry for it.
        googleTvsHomeComesToTheFront()

        assertNull(startedActivity)
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
    fun homeClosesTheScreensaverAndOpensBcLauncher() {
        screensaver(Intent.ACTION_DREAMING_STARTED)

        pressHome()
        idleFor(handOffDelay)

        assertEquals(
            listOf(
                AccessibilityService.GLOBAL_ACTION_BACK,
                AccessibilityService.GLOBAL_ACTION_HOME,
            ),
            globalActions,
        )
        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun holdingHomeClosesTheScreensaverAndOpensGoogleTvsPanel() {
        screensaver(Intent.ACTION_DREAMING_STARTED)

        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)

        assertEquals(
            listOf(
                AccessibilityService.GLOBAL_ACTION_BACK,
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            ),
            globalActions,
        )
    }

    @Test
    fun homeSendsNoBackOnceTheScreensaverHasClosed() {
        screensaver(Intent.ACTION_DREAMING_STARTED)
        screensaver(Intent.ACTION_DREAMING_STOPPED)

        pressHome()
        idleFor(handOffDelay)

        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_HOME), globalActions)
        assertOpenedBcLauncher(startedActivity)
    }
}

package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.data.FakeAccessibilitySource
import io.github.bryancassell.bclauncher.data.HomeButtonRepository
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityRecord
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class HomeButtonServiceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val controller: ServiceController<HomeButtonService> =
        Robolectric.buildService(HomeButtonService::class.java)
    private val service: HomeButtonService by lazy {
        // Before API 33, ContextCompat registers the service's receiver for BC Launcher alone with this permission,
        // which androidx.core's manifest declares and Android grants at install, but Robolectric doesn't.
        shadowOf(
            application,
        ).grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        controller.create().get()
    }

    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    // How long after handing Home to Android BC Launcher opens.
    private val handOffDelay = 100L

    private val googleTvHome = ComponentName(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.apps.tv.launcherx.home.HomeActivity",
    )
    private val googleTvPanel = "com.google.android.apps.tv.launcherx.dashboard.DashboardActivity"
    private val youTube = "com.google.android.youtube.tv"

    // The window in front, and one behind it.
    private val frontWindow = 1
    private val hiddenWindow = 2

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

    private val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    // Google TV's launcher is the system's Home app, as on a Google TV device.
    private fun installGoogleTvHome() {
        shadowOf(application.packageManager).apply {
            addActivityIfNotPresent(googleTvHome)
            addIntentFilterForActivity(googleTvHome, homeFilter)
        }
    }

    // The windows on screen: one in front, from packageName, or none.
    private fun setInFront(
        packageName: String?,
        type: Int = AccessibilityWindowInfo.TYPE_APPLICATION,
    ) {
        if (packageName == null) {
            shadowOf(service).setWindows(emptyList())
            return
        }
        @Suppress("DEPRECATION")
        val root = AccessibilityNodeInfo.obtain().apply { this.packageName = packageName }

        @Suppress("DEPRECATION")
        val window = AccessibilityWindowInfo.obtain()
        shadowOf(window).apply {
            setId(frontWindow)
            setType(type)
            setActive(true)
            setRoot(root)
        }
        shadowOf(service).setWindows(listOf(window))
    }

    // Asking the window in front for its package runs on the service's own thread.
    private fun connectWithInFront(packageName: String?) {
        setInFront(packageName)
        service.onServiceConnected()
        ShadowLooper.getAllLoopers().filter {
            it != Looper.getMainLooper()
        }.forEach { shadowOf(it).idle() }
        idleFor(0)
    }

    // As the system does when the screensaver starts or stops. Sent from the service, so it's running to receive it.
    private fun screensaver(action: String) {
        service.sendBroadcast(Intent(action))
        idleFor(0)
    }

    private fun windowEventFrom(
        packageName: String,
        className: String?,
        windowId: Int = frontWindow,
    ) {
        @Suppress("DEPRECATION")
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = packageName
        event.className = className
        Shadow.extract<ShadowAccessibilityRecord>(event).setWindowId(windowId)
        service.onAccessibilityEvent(event)
    }

    private fun windowComesToTheFront(packageName: String, className: String?) {
        setInFront(packageName)
        windowEventFrom(packageName, className)
    }

    private fun googleTvsHomeComesToTheFront() {
        windowComesToTheFront(googleTvHome.packageName, googleTvHome.className)
    }

    // As the home screen's Google TV button does: it tells the service, and once the service has been told, it starts
    // Google TV's home, which comes to the front.
    private fun openGoogleTvsHomeFromBcLauncher() {
        var opened = false
        HomeButtonRepository(application, FakeAccessibilitySource()).openingGoogleTvHome {
            opened = true
            googleTvsHomeComesToTheFront()
        }
        idleFor(0)
        assertTrue(opened)
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

        // As when Google TV's launcher starts its home again itself.
        googleTvsHomeComesToTheFront()

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun googleTvsHomeBehindBcLauncherAfterAHandOffOpensNothing() {
        handOffWithGoogleTvHome()

        // Google TV's home sends window events while BC Launcher covers it.
        windowEventFrom(googleTvHome.packageName, googleTvHome.className, hiddenWindow)

        assertNull(startedActivity)
    }

    @Test
    fun googleTvsPanelAfterAHandOffEndsTheWait() {
        handOffWithGoogleTvHome()

        // As when another key opens Google TV's panel. Its home then sends a window event from behind it.
        windowComesToTheFront(googleTvHome.packageName, googleTvPanel)
        windowEventFrom(googleTvHome.packageName, googleTvHome.className)

        assertNull(startedActivity)
    }

    @Test
    fun anotherAppAfterAHandOffEndsTheWait() {
        handOffWithGoogleTvHome()

        windowComesToTheFront(youTube, "$youTube.MainActivity")
        googleTvsHomeComesToTheFront()

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
    fun aPressDuringTheHandOffDelayStillOpensBcLauncherWithoutTheWait() {
        installGoogleTvHome()
        connectWithInFront(application.packageName)
        pressHome()
        idleFor(handOffDelay - 40)

        // The user holds Home again for the panel.
        send(KeyEvent.ACTION_DOWN)
        idleFor(40)
        assertOpenedBcLauncher(startedActivity)
        googleTvsHomeComesToTheFront()

        assertNull(startedActivity)
    }

    @Test
    fun openingGoogleTvsHomeFromBcLauncherEndsTheWaitAfterAHandOff() {
        handOffWithGoogleTvHome()

        openGoogleTvsHomeFromBcLauncher()

        assertNull(startedActivity)
    }

    @Test
    fun openingGoogleTvsHomeFromBcLauncherEndsTheWaitAfterConnecting() {
        installGoogleTvHome()
        connectWithInFront(null)

        openGoogleTvsHomeFromBcLauncher()

        assertNull(startedActivity)
    }

    @Test
    fun openingGoogleTvsHomeFromBcLauncherDuringTheHandOffDelayOpensNothing() {
        installGoogleTvHome()
        connectWithInFront(application.packageName)
        pressHome()
        idleFor(handOffDelay - 40)

        openGoogleTvsHomeFromBcLauncher()
        idleFor(handOffDelay)

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
        googleTvsHomeComesToTheFront()

        assertNull(startedActivity)
    }

    @Test
    fun opensBcLauncherWhenGoogleTvsHomeIsTheFirstWindowAfterItConnects() {
        installGoogleTvHome()

        connectWithInFront(null)
        assertNull(startedActivity)
        googleTvsHomeComesToTheFront()

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun leavesTheFirstAppAfterItConnectsWhenItIsNotGoogleTvsHome() {
        installGoogleTvHome()

        connectWithInFront(null)
        windowComesToTheFront(youTube, "$youTube.MainActivity")
        googleTvsHomeComesToTheFront()

        assertNull(startedActivity)
    }

    @Test
    fun googleTvsHomeBehindTheFirstAppOpensNothing() {
        installGoogleTvHome()
        connectWithInFront(null)

        // The TV started in an HDMI input, with Google TV's home behind it.
        setInFront("com.example.hdmiinput")
        windowEventFrom(googleTvHome.packageName, googleTvHome.className, hiddenWindow)

        assertNull(startedActivity)
    }

    @Test
    fun androidsOwnDialogsDoNotEndTheWait() {
        installGoogleTvHome()
        connectWithInFront(null)

        // As Android's "Optimizing apps" dialog after an update.
        setInFront("android", AccessibilityWindowInfo.TYPE_SYSTEM)
        windowEventFrom("android", "android.app.AlertDialog")
        googleTvsHomeComesToTheFront()

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun googleTvsWindowsThatAreNotActivitiesDoNotEndTheWait() {
        installGoogleTvHome()
        connectWithInFront(null)

        windowComesToTheFront(googleTvHome.packageName, "android.widget.FrameLayout")
        googleTvsHomeComesToTheFront()

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun aHomePressEndsTheWaitAfterConnecting() {
        installGoogleTvHome()
        connectWithInFront(null)

        // The user holds Home for the panel before any window has come to the front.
        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)
        windowComesToTheFront(googleTvHome.packageName, googleTvPanel)
        googleTvsHomeComesToTheFront()

        assertNull(startedActivity)
    }

    @Test
    fun opensNothingOnConnectingWithoutAHomeApp() {
        connectWithInFront(googleTvHome.packageName)

        assertNull(startedActivity)
    }

    @Test
    fun recognizesGoogleTvsHomeWhenItsHomeFilterIsOnAnAlias() {
        val alias = ActivityInfo().apply {
            packageName = googleTvHome.packageName
            name = "${googleTvHome.packageName}.HomeAlias"
            targetActivity = googleTvHome.className
            applicationInfo = ApplicationInfo().apply { packageName = googleTvHome.packageName }
        }
        shadowOf(application.packageManager).apply {
            addOrUpdateActivity(alias)
            addIntentFilterForActivity(ComponentName(alias.packageName, alias.name), homeFilter)
        }
        connectWithInFront(null)

        // Window events name the activity, not the alias.
        googleTvsHomeComesToTheFront()

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun homeDuringTheScreensaverHandsItToAndroid() {
        screensaver(Intent.ACTION_DREAMING_STARTED)

        pressHome()
        idleFor(handOffDelay)

        // Android closes the screensaver for it, so there's no Back.
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_HOME), globalActions)
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
    fun holdingHomeSendsNoBackOnceTheScreensaverHasClosed() {
        screensaver(Intent.ACTION_DREAMING_STARTED)
        screensaver(Intent.ACTION_DREAMING_STOPPED)

        send(KeyEvent.ACTION_DOWN)
        idleFor(longPressTimeout)

        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), globalActions)
    }
}

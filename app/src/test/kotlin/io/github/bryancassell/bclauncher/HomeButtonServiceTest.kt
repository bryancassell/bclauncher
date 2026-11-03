package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.bryancassell.bclauncher.data.DataStoreModule
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@UninstallModules(DataStoreModule::class)
@RunWith(AndroidJUnit4::class)
class HomeButtonServiceTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.Main)

    /** Runs on the main looper, so idling it finishes everything the service started. */
    @BindValue
    @JvmField
    val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(folder.root, "test.preferences_pb")
        }

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val controller: ServiceController<HomeButtonService> =
        Robolectric.buildService(HomeButtonService::class.java)
    private val service: HomeButtonService by lazy { controller.create().get() }

    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    @After
    fun stopDataStore() {
        dataStoreScope.cancel()
    }

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun send(action: Int, keyCode: Int = KeyEvent.KEYCODE_HOME) =
        service.onKeyEvent(KeyEvent(action, keyCode))

    private val startedActivity: Intent? get() = shadowOf(application).nextStartedActivity

    private val globalActions: List<Int> get() = shadowOf(service).globalActionsPerformed

    private fun assertOpenedBcLauncher(started: Intent?) {
        assertEquals(ComponentName(application, MainActivity::class.java), started?.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started?.flags)
    }

    private fun setStartCount(count: Int) {
        Settings.Global.putInt(application.contentResolver, Settings.Global.BOOT_COUNT, count)
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
    fun turningTheServiceOnLeavesTheScreenAsItIs() {
        setStartCount(4)

        service.onServiceConnected()
        idleFor(0)

        assertNull(startedActivity)
    }

    @Test
    fun opensBcLauncherWhenItConnectsAfterTheTvRestarts() {
        setStartCount(4)
        service.onServiceConnected()
        idleFor(0)

        // Android connects it again once the TV has started.
        setStartCount(5)
        service.onServiceConnected()
        idleFor(0)

        assertOpenedBcLauncher(startedActivity)
    }

    @Test
    fun connectingAgainWithoutARestartLeavesTheScreenAsItIs() {
        setStartCount(4)
        service.onServiceConnected()
        idleFor(0)

        // As after an update, or when Android restarts the service after a crash.
        service.onServiceConnected()
        idleFor(0)

        assertNull(startedActivity)
    }
}

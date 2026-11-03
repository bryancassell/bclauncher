package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeButtonRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accessibilitySource = FakeAccessibilitySource()
    private val repository = HomeButtonRepository(context, accessibilitySource)

    private val reader = ComponentName("com.example.reader", "com.example.reader.ReaderService")

    @Test
    fun isOffWhileItsServiceIsOff() = runTest {
        accessibilitySource.setEnabledServices(reader)

        assertFalse(repository.isOn.first())
    }

    @Test
    fun isOnWhileItsServiceIsOn() = runTest {
        accessibilitySource.setEnabledServices(reader, homeButtonService(context))

        assertTrue(repository.isOn.first())
    }

    @Test
    fun emitsAgainOnlyWhenItsServiceIsTurnedOnOrOff() = runTest {
        val emitted = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.isOn.toList(emitted)
        }

        // Another service doesn't change what Home opens.
        accessibilitySource.setEnabledServices(reader)
        accessibilitySource.setEnabledServices(reader, homeButtonService(context))
        accessibilitySource.setEnabledServices()

        assertEquals(listOf(false, true, false), emitted)
    }

    @Test
    fun settingsIntentOpensAccessibilitySettingsInTheirOwnTask() {
        val intent = repository.settingsIntent

        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }
}

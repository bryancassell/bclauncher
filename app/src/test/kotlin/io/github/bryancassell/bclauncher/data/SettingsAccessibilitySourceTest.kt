package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilitySourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = SettingsAccessibilitySource(context)

    private val reader = ComponentName("com.example.reader", "com.example.reader.ReaderService")
    private val keys = ComponentName("com.example.keys", "com.example.keys.KeyService")

    // As Accessibility settings does when the user turns a service on or off. Robolectric tells observers, as the
    // setting's provider does.
    private fun setEnabledServices(value: String?) {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            value,
        )
    }

    @Test
    fun noServicesAreOnWhenTheSettingIsUnset() = runTest {
        assertEquals(emptySet<ComponentName>(), source.enabledServices.first())
    }

    @Test
    fun noServicesAreOnWhenTheSettingIsEmpty() = runTest {
        setEnabledServices("")

        assertEquals(emptySet<ComponentName>(), source.enabledServices.first())
    }

    @Test
    fun readsServicesNamedInFullOrRelativeToTheirPackage() = runTest {
        setEnabledServices(
            "com.example.reader/com.example.reader.ReaderService:com.example.keys/.KeyService",
        )

        assertEquals(setOf(reader, keys), source.enabledServices.first())
    }

    @Test
    fun emitsAgainWhenTheUserTurnsAServiceOnOrOff() = runTest {
        val emitted = mutableListOf<Set<ComponentName>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.enabledServices.toList(emitted)
        }

        setEnabledServices(reader.flattenToString())
        shadowOf(Looper.getMainLooper()).idle()
        setEnabledServices(null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(emptySet(), setOf(reader), emptySet()), emitted)
    }
}

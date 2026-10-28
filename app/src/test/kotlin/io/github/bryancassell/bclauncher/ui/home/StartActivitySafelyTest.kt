package io.github.bryancassell.bclauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartActivitySafelyTest {
    private val intent = Intent(Intent.ACTION_MAIN)

    @Test
    fun startsTheActivity() {
        val context = FakeContext(failure = null)

        assertTrue(context.startActivitySafely(intent))
        assertEquals(listOf(intent), context.started)
    }

    @Test
    fun failsWhenTheActivityIsGone() {
        assertFalse(FakeContext(ActivityNotFoundException()).startActivitySafely(intent))
    }

    @Test
    fun failsWhenBcLauncherMayNotStartTheActivity() {
        assertFalse(FakeContext(SecurityException()).startActivitySafely(intent))
    }

    private class FakeContext(private val failure: RuntimeException?) :
        ContextWrapper(ApplicationProvider.getApplicationContext()) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            if (failure != null) throw failure
            started += intent
        }
    }
}

package io.github.bryancassell.bclauncher.data

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledAppTest {
    @Test
    fun launchIntentOpensTheTvActivityInItsOwnTask() {
        val app = fakeApp("YouTube")

        val intent = app.launchIntent

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(setOf(Intent.CATEGORY_LEANBACK_LAUNCHER), intent.categories)
        assertEquals(app.component, intent.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, intent.flags)
    }
}

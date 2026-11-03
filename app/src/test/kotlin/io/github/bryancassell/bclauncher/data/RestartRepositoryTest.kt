package io.github.bryancassell.bclauncher.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestartRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun TestScope.repository() = RestartRepository(
        context,
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "test.preferences_pb")
        },
    )

    // Android counts each start of the TV.
    private fun setStartCount(count: Int) {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, count)
    }

    @Test
    fun theFirstCallEverIsNotARestart() = runTest {
        setStartCount(4)

        assertFalse(repository().hasRestartedSinceLastCall())
    }

    @Test
    fun aLaterCallInTheSameStartIsNotARestart() = runTest {
        setStartCount(4)
        val repository = repository()
        repository.hasRestartedSinceLastCall()

        assertFalse(repository.hasRestartedSinceLastCall())
    }

    @Test
    fun theFirstCallAfterTheTvRestartsIsARestart() = runTest {
        setStartCount(4)
        val repository = repository()
        repository.hasRestartedSinceLastCall()

        setStartCount(5)

        assertTrue(repository.hasRestartedSinceLastCall())
        assertFalse(repository.hasRestartedSinceLastCall())
    }
}

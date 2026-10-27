package io.github.bryancassell.bclauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app's Hilt graph builds the repository with the IO dispatcher and, in tests, the fake source. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DataModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: InstalledAppsRepository

    @Inject
    lateinit var packageSource: FakePackageSource

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun injectedRepositoryListsTheSourcesApps() = runTest {
        packageSource.setApps(fakeApp("YouTube"))

        val apps = repository.apps.first()

        assertEquals(listOf("YouTube"), apps.map { it.label })
    }
}

package io.github.bryancassell.bclauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Qualifies the dispatcher for blocking calls (ARCHITECTURE.md, Dependency injection). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

/**
 * Its own module, so activity tests can replace it with a fake source. An interface rather than an abstract class,
 * since an abstract class's constructor counts as a line the coverage check needs a test to run (#4).
 */
@Module
@InstallIn(SingletonComponent::class)
interface PackageSourceModule {
    @Binds
    fun packageSource(source: PackageManagerSource): PackageSource
}

/** Its own module, so activity tests can replace it with a fake source, as with [PackageSourceModule]. */
@Module
@InstallIn(SingletonComponent::class)
interface AccessibilitySourceModule {
    @Binds
    fun accessibilitySource(source: SettingsAccessibilitySource): AccessibilitySource
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /**
     * One instance, since DataStore allows only one per file in a process. In noBackupFilesDir, so Android's backup
     * leaves it out: [RestartRepository]'s count of TV starts would mean nothing on another TV, or on this one after a
     * restore.
     */
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.noBackupFilesDir, "bclauncher.preferences_pb")
        }
}

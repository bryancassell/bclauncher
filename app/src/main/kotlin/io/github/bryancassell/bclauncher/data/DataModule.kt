package io.github.bryancassell.bclauncher.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "DataModule"

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

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /**
     * A singleton, since DataStore fails when two instances in a process use the same file (ARCHITECTURE.md, Stored
     * data). A file that can't be parsed is replaced with an empty order, so the apps show alphabetically rather than
     * every read failing. That's logged, so a release whose serializer R8 broke shows it in logcat.
     */
    @Provides
    @Singleton
    fun appOrderStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<AppOrder> = DataStoreFactory.create(
        serializer = AppOrderSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { error ->
            Log.e(TAG, "Replacing the app order, which couldn't be parsed", error)
            AppOrder()
        },
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.dataStoreFile("app_order.json") },
    )
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

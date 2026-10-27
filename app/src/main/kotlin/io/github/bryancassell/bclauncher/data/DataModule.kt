package io.github.bryancassell.bclauncher.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
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

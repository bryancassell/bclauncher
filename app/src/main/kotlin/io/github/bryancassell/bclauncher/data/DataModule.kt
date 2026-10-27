package io.github.bryancassell.bclauncher.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/** Marks the dispatcher for blocking calls, injected so tests can pass a test dispatcher. */
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

/** Its own module, so activity tests can replace it with a fake source. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PackageSourceModule {
    @Binds
    abstract fun packageSource(source: PackageManagerSource): PackageSource
}

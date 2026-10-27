package io.github.bryancassell.bclauncher.data

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** Gives every Hilt test the fake source, so activity tests choose the installed apps. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PackageSourceModule::class])
interface FakePackageSourceModule {
    @Binds
    fun packageSource(source: FakePackageSource): PackageSource
}

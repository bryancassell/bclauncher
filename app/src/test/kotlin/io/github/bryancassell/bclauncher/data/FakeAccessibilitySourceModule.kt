package io.github.bryancassell.bclauncher.data

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** Gives every Hilt test the fake source, so activity tests choose whether Home opens BC Launcher. */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AccessibilitySourceModule::class],
)
interface FakeAccessibilitySourceModule {
    @Binds
    fun accessibilitySource(source: FakeAccessibilitySource): AccessibilitySource
}

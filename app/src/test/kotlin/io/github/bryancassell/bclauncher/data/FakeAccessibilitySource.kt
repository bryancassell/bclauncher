package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Context
import io.github.bryancassell.bclauncher.HomeButtonService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** An [AccessibilitySource] whose services the test turns on. In a Hilt test, the app gets this same instance. */
@Singleton
class FakeAccessibilitySource @Inject constructor() : AccessibilitySource {
    private val services = MutableStateFlow<Set<ComponentName>>(emptySet())

    @Volatile
    private var failure: Throwable? = null

    override val enabledServices: Flow<Set<ComponentName>> = flow {
        failure?.let { throw it }
        emitAll(services)
    }

    /** Makes reading the services throw [error], as the settings provider might. */
    fun failReading(error: Throwable) {
        failure = error
    }

    /** Turns on [components] and turns off every other service, as the user would in Accessibility settings. */
    fun setEnabledServices(vararg components: ComponentName) {
        services.value = components.toSet()
    }
}

/** BC Launcher's service, which makes Home open it. */
fun homeButtonService(context: Context) = ComponentName(context, HomeButtonService::class.java)

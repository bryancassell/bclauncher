package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import kotlinx.coroutines.flow.Flow

/** The accessibility services the user has turned on, as the platform reports them. Tests replace it with a fake. */
interface AccessibilitySource {
    /** Emits the services that are on as soon as it's listening, then again each time the user turns one on or off. */
    val enabledServices: Flow<Set<ComponentName>>
}

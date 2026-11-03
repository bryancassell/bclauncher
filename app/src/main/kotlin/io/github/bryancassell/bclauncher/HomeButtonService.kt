package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import io.github.bryancassell.bclauncher.data.RestartRepository
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Makes the remote's Home button open BC Launcher while the user has this service on in Accessibility settings, since
 * Google TV doesn't let another app be the Home app (ARCHITECTURE.md, Home button).
 *
 * It filters the remote's keys and takes every press of Home. A short press opens BC Launcher. A long press opens
 * Google TV's panel, as Home does without this service (PRD.md, Design decisions). GLOBAL_ACTION_NOTIFICATIONS starts
 * the same panel, in Google TV's launcher, that SystemUI starts for a long press.
 *
 * The filter gets no key repeats, so no event has the long-press flag: on a Google TV Streamer, a press held for 5
 * seconds came as one DOWN and one UP. So a press held for the long-press timeout counts as long, and the panel opens
 * then, while Home is still held, as it does without the service (#10).
 */
@AndroidEntryPoint
class HomeButtonService : AccessibilityService() {
    @Inject
    lateinit var restarts: RestartRepository

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private var longPressed = false
    private val longPress = Runnable {
        longPressed = true
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * After the TV restarts, the system starts its Home app, Google TV, so this opens BC Launcher over it. The service
     * also connects when the user turns it on, and after an update or a crash, which aren't restarts (#10).
     *
     * Public, as are the other callbacks, so tests can call it as the system does.
     */
    public override fun onServiceConnected() {
        scope.launch {
            if (restarts.hasRestartedSinceLastCall()) openBcLauncher()
        }
    }

    public override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                longPressed = false
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }

            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!longPressed) openBcLauncher()
            }
        }
        return true
    }

    // MainActivity is singleTask, so this returns to the one home screen rather than opening another on top of it.
    private fun openBcLauncher() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // Its configuration asks for no events.
    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(longPress)
        scope.cancel()
        super.onDestroy()
    }
}

package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import io.github.bryancassell.bclauncher.data.resolveHomeActivity

/**
 * How long after handing Home to Android BC Launcher opens. On a Google TV Streamer, Google TV's home started 25 to 39
 * ms after GLOBAL_ACTION_HOME, and BC Launcher started up to 96 ms after it covered it before it drew, while BC Launcher
 * started before it was covered by it. Waiting for a signal instead left Google TV on screen: its window event came
 * about 300 ms after the hand-off, and the "homekey" ACTION_CLOSE_SYSTEM_DIALOGS broadcast as late as 515 ms (#10).
 */
private const val HAND_OFF_DELAY_MS = 100L

/**
 * How long after a hand-off BC Launcher opens again if Google TV's home is in front anyway. That happens on a TV where
 * it starts later than [HAND_OFF_DELAY_MS], and when Google TV's launcher starts its home again itself: on a Google TV
 * Streamer, it did so 450 ms after a hand-off, logging "Background timeout reached, starting home intent", and its home
 * showed for about 500 ms until BC Launcher came back. The next press of Home, or another app or another of Google TV's
 * activities coming to the front, ends the wait sooner (#10).
 */
private const val HAND_OFF_GUARD_MS = 1_500L

/**
 * Makes the remote's Home button open BC Launcher while the user has this service on in Accessibility settings, since
 * Google TV doesn't let another app be the Home app (ARCHITECTURE.md, Home button).
 *
 * It filters the remote's keys and takes every press of Home. A short press goes on to Android as GLOBAL_ACTION_HOME,
 * which turns the TV on and switches it to this input over HDMI-CEC, as Home does without this service, and which
 * only a system app can do directly. It also closes the screensaver. BC Launcher then opens in place of the Google TV
 * home that Android starts. A long press opens Google TV's panel, as Home does without this service (PRD.md, Design
 * decisions). GLOBAL_ACTION_NOTIFICATIONS starts the same panel, in Google TV's launcher, that SystemUI starts for a
 * long press.
 *
 * The filter gets no key repeats, so no event has the long-press flag: on a Google TV Streamer, a press held for 5
 * seconds came as one DOWN and one UP. So a press held for the long-press timeout counts as long, and the panel opens
 * then, while Home is still held, as it does without the service (#10).
 */
class HomeButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    // Asking which package a window is from waits for that app's main thread, so it's done here rather than on the
    // main thread, where every key waits for onKeyEvent.
    private val queries = HandlerThread("HomeButtonService queries")
    private lateinit var queryHandler: Handler

    /**
     * The screensaver would cover the panel, so a long press closes it first with Back. On a Google TV Streamer, Back
     * closed Google TV's screensaver. A short press needs no Back, since Android closes the screensaver for
     * GLOBAL_ACTION_HOME (#10).
     */
    private val longPress = Runnable {
        if (dreaming) performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    private var armsGuard = false
    private val openAfterHandOff = Runnable {
        openBcLauncher()
        if (armsGuard) handOffGuardUntil = SystemClock.uptimeMillis() + HAND_OFF_GUARD_MS
    }
    private var handOffGuardUntil = 0L

    private var dreaming = false
    private val dreams = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dreaming = intent.action == Intent.ACTION_DREAMING_STARTED
        }
    }

    /**
     * Ends both waits, and any hand-off still to open BC Launcher, when the home screen's Google TV button opens Google
     * TV's home, so BC Launcher doesn't open in its place (#6). The button starts Google TV's home only once this has
     * run, as the ordered broadcast's result (HomeButtonRepository.openingGoogleTvHome).
     */
    private val openingGoogleTvHome = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(openAfterHandOff)
            endWaits()
        }
    }

    // The system's Home activity, Google TV's home, from when the service connects.
    private var googleTvHome: ComponentName? = null

    // From when the service connects until it knows which app is in front.
    private var waitingForFront = false

    // The system sends these only to receivers registered while it runs.
    override fun onCreate() {
        super.onCreate()
        queries.start()
        queryHandler = Handler(queries.looper)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dreams, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dreams, filter)
        }
        // Not exported, so only BC Launcher can end the waits. Before API 33, ContextCompat has the sender hold a
        // signature permission that androidx.core's manifest declares.
        ContextCompat.registerReceiver(
            this,
            openingGoogleTvHome,
            IntentFilter(ACTION_OPENING_GOOGLE_TV_HOME),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * When the TV starts, the system starts its Home app, Google TV, and this opens BC Launcher in its place. It does so
     * only when Google TV's home is in front, so it leaves alone an app the user or the TV opened first, such as an
     * HDMI input, and Accessibility settings when the user has just turned the service on. When no application window
     * is in front yet, the first one to come to the front decides. On the Google TV emulator (API 36), Google TV's home
     * was already in front when the service connected after a reboot (#10).
     *
     * Public, as are the other callbacks, so tests can call it as the system does.
     */
    public override fun onServiceConnected() {
        // The window's activity, not an <activity-alias> the Home filter may be on, is what window events name.
        googleTvHome =
            packageManager.resolveHomeActivity()?.activityInfo?.let {
                ComponentName(
                    it.packageName,
                    it.targetActivity ?: it.name,
                )
            }
        waitingForFront = true
        queryHandler.post {
            val front = frontApplicationWindow()?.root?.packageName
            handler.post { connectedWith(front) }
        }
    }

    // A window's root names only its package, so any of Google TV's windows counts as its home here.
    private fun connectedWith(frontPackage: CharSequence?) {
        if (!waitingForFront || frontPackage == null) return
        waitingForFront = false
        if (frontPackage == googleTvHome?.packageName) openBcLauncher()
    }

    /**
     * Window state changes are the only events the service's configuration asks for, and they matter only while it
     * waits after connecting or after a hand-off. Google TV's home also sends them while it's hidden behind another
     * app, and Android's own dialogs send them too, so only the application window in front counts. The window list
     * comes from the system, so reading it doesn't wait for an app (#10).
     */
    public override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val home = googleTvHome ?: return
        if (!waitingForFront && SystemClock.uptimeMillis() >= handOffGuardUntil) return
        if (frontApplicationWindow()?.id != event.windowId) return
        val packageName = event.packageName ?: return
        val className = event.className?.toString()
        when {
            packageName == home.packageName && className == home.className -> {
                endWaits()
                openBcLauncher()
            }

            // Google TV's windows that aren't activities, such as the FrameLayout ones its home sends, say nothing.
            packageName == home.packageName && className?.startsWith(
                "${home.packageName}.",
            ) != true -> Unit

            // BC Launcher in front ends the wait after connecting, but not the one after a hand-off, since Google TV's
            // home can still start itself again.
            packageName == this.packageName -> waitingForFront = false

            // Another app, or another of Google TV's activities, such as its panel.
            else -> endWaits()
        }
    }

    // Ends the wait after connecting and the wait after a hand-off.
    private fun endWaits() {
        waitingForFront = false
        handOffGuardUntil = 0L
    }

    private fun frontApplicationWindow(): AccessibilityWindowInfo? =
        windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }

    public override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            // A new press ends both waits, so what it opens isn't covered. A second DOWN with no UP between them
            // starts the timing again, so the panel can't open early or twice.
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                endWaits()
                armsGuard = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }

            // A long press still to come means the press was short. A cancelled one, as when the remote disconnects
            // while Home is held, does nothing.
            KeyEvent.ACTION_UP -> if (handler.hasCallbacks(longPress)) {
                handler.removeCallbacks(longPress)
                if (!event.isCanceled) handOff()
            }
        }
        return true
    }

    private fun handOff() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        armsGuard = true
        handler.removeCallbacks(openAfterHandOff)
        handler.postDelayed(openAfterHandOff, HAND_OFF_DELAY_MS)
    }

    // MainActivity is singleTask, so this returns to the one home screen rather than opening another on top of it.
    private fun openBcLauncher() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        queries.quitSafely()
        unregisterReceiver(dreams)
        unregisterReceiver(openingGoogleTvHome)
        super.onDestroy()
    }

    companion object {
        /** Sent to BC Launcher alone, just before the home screen opens Google TV's home. */
        const val ACTION_OPENING_GOOGLE_TV_HOME =
            "io.github.bryancassell.bclauncher.action.OPENING_GOOGLE_TV_HOME"
    }
}

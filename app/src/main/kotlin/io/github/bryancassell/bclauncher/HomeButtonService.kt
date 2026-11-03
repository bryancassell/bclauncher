package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

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
 * showed for about 500 ms until BC Launcher came back. The next press of Home ends the wait sooner (#10).
 */
private const val HAND_OFF_GUARD_MS = 1_500L

/**
 * Makes the remote's Home button open BC Launcher while the user has this service on in Accessibility settings, since
 * Google TV doesn't let another app be the Home app (ARCHITECTURE.md, Home button).
 *
 * It filters the remote's keys and takes every press of Home. A short press goes on to Android as GLOBAL_ACTION_HOME,
 * which turns the TV on and switches it to this input over HDMI-CEC, as Home does without this service, and which
 * only a system app can do directly. BC Launcher then opens in place of the Google TV home that Android starts. A long
 * press opens Google TV's panel, as Home does without this service (PRD.md, Design decisions).
 * GLOBAL_ACTION_NOTIFICATIONS starts the same panel, in Google TV's launcher, that SystemUI starts for a long press.
 *
 * The filter gets no key repeats, so no event has the long-press flag: on a Google TV Streamer, a press held for 5
 * seconds came as one DOWN and one UP. So a press held for the long-press timeout counts as long, and the panel opens
 * then, while Home is still held, as it does without the service (#10).
 *
 * Taking Home also keeps it from the screensaver, so the service closes the screensaver with Back as Home goes down.
 * Otherwise Google TV's panel would open behind it. On a Google TV Streamer, Back closed Google TV's screensaver (#10).
 */
class HomeButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
    private val openAfterHandOff = Runnable {
        openBcLauncher()
        handOffGuardUntil = SystemClock.uptimeMillis() + HAND_OFF_GUARD_MS
    }
    private var handOffGuardUntil = 0L

    private var dreaming = false
    private val dreams = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dreaming = intent.action == Intent.ACTION_DREAMING_STARTED
        }
    }

    // The system's Home activity, Google TV's home, from when the service connects.
    private var googleTvHome: ComponentName? = null

    // From when the service connects until it knows which app is in front.
    private var waitingForFront = false

    // The system sends these only to receivers registered while it runs.
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dreams, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dreams, filter)
        }
    }

    /**
     * When the TV starts, the system starts its Home app, Google TV, and this opens BC Launcher in its place. It does so
     * only when Google TV's home is in front, so it leaves alone an app the user or the TV opened first, such as an
     * HDMI input, and Accessibility settings when the user has just turned the service on. When the service connects
     * before any window is in front, the first one to come to the front decides. On the Google TV emulator (API 36),
     * Google TV's home was already in front when the service connected after a reboot (#10).
     *
     * Public, as are the other callbacks, so tests can call it as the system does.
     */
    public override fun onServiceConnected() {
        googleTvHome = resolveHome()?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        waitingForFront = true
        rootInActiveWindow?.packageName?.let(::firstInFront)
    }

    /**
     * Window state changes are the only events the service's configuration asks for. After a hand-off, only Google
     * TV's home activity counts, not its panel, which is in the same package. Its home also sends them while it's
     * hidden behind BC Launcher, about 800 ms later on a Google TV Streamer, so the window in front decides too (#10).
     */
    public override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName ?: return
        val home = googleTvHome ?: return
        if (waitingForFront) {
            firstInFront(packageName)
        } else if (event.className == home.className &&
            SystemClock.uptimeMillis() < handOffGuardUntil &&
            rootInActiveWindow?.packageName == home.packageName
        ) {
            handOffGuardUntil = 0L
            openBcLauncher()
        }
    }

    // A window's root says only which package it's from, so any of Google TV's windows counts here.
    private fun firstInFront(packageName: CharSequence) {
        waitingForFront = false
        if (packageName == googleTvHome?.packageName) openBcLauncher()
    }

    // BC Launcher declares no Home filter, so this is the system's Home app, which the manifest's <queries> makes
    // visible from API 30.
    private fun resolveHome(): ResolveInfo? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0)
        }
    }

    public override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            // A second DOWN with no UP between them starts the timing again, so the panel can't open early or twice.
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                handOffGuardUntil = 0L
                if (dreaming) performGlobalAction(GLOBAL_ACTION_BACK)
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
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(openAfterHandOff)
        unregisterReceiver(dreams)
        super.onDestroy()
    }
}

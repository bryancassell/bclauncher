package io.github.bryancassell.bclauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

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
class HomeButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }

    // Google TV's home, from when the service connects until it knows which app is in front.
    private var googleTvHome: String? = null

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
        googleTvHome = resolveHome()?.activityInfo?.packageName
        replaceGoogleTvHome(frontPackage = rootInActiveWindow?.packageName)
    }

    // Window state changes are the only events the service's configuration asks for.
    public override fun onAccessibilityEvent(event: AccessibilityEvent) {
        replaceGoogleTvHome(frontPackage = event.packageName)
    }

    private fun replaceGoogleTvHome(frontPackage: CharSequence?) {
        val home = googleTvHome ?: return
        if (frontPackage == null) return
        googleTvHome = null
        if (frontPackage == home) openBcLauncher()
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
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }

            // A long press still to come means the press was short. A cancelled one, as when the remote disconnects
            // while Home is held, opens nothing.
            KeyEvent.ACTION_UP -> if (handler.hasCallbacks(longPress)) {
                handler.removeCallbacks(longPress)
                if (!event.isCanceled) openBcLauncher()
            }
        }
        return true
    }

    // MainActivity is singleTask, so this returns to the one home screen rather than opening another on top of it.
    private fun openBcLauncher() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(longPress)
        super.onDestroy()
    }
}

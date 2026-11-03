package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

/** An app the home screen lists: one activity that opens from a TV's app list. */
data class InstalledApp(
    val component: ComponentName,
    val label: String,
    /** The image its tile shows, or null when the app has none and the tile shows [icon] (PRD.md, Design decisions). */
    val banner: Drawable?,
    val icon: Drawable,
    /** Whether the app is part of the system image, which the user can't uninstall, even after updating it. */
    val isSystemApp: Boolean,
) {
    /** Opens the app in its own task, or brings that task to the front if the app is already running. */
    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /**
     * Opens the app's App info screen in Settings. Clearing Settings' task first means Back returns to BC Launcher,
     * not to a Settings screen the user left open earlier.
     */
    val appSettingsIntent: Intent
        get() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** Asks the user to confirm uninstalling the app, which needs the manifest's REQUEST_DELETE_PACKAGES. */
    val uninstallIntent: Intent
        get() = Intent(Intent.ACTION_DELETE, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val packageUri: Uri
        get() = Uri.fromParts("package", component.packageName, null)
}

package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable

/** An app the home screen lists: one activity that opens from a TV's app list. */
data class InstalledApp(
    val component: ComponentName,
    val label: String,
    /** The image its tile shows, or null when the app has none and the tile shows [icon] (PRD.md, Design decisions). */
    val banner: Drawable?,
    val icon: Drawable,
) {
    /** Opens the app in its own task, or brings that task to the front if the app is already running. */
    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

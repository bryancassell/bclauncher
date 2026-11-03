package io.github.bryancassell.bclauncher.data

import android.content.Intent
import android.provider.Settings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The screens of Android's own that the home screen opens: Google TV's home and device settings. */
class SystemScreensRepository @Inject constructor(
    packageSource: PackageSource,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    /**
     * The intent that opens Google TV's home, or null when there's no Home activity to open, emitted again when a
     * package change changes which that is, as when Google TV's launcher is turned off or on. It names the activity it
     * found, so the button opens the one whose presence showed it. As a Home intent, it brings Google TV's existing
     * home task to the front.
     */
    val googleTvHomeIntent: Flow<Intent?> = packageSource.changes
        .conflate()
        .map { packageSource.homeActivity() }
        .distinctUntilChanged()
        .map { home ->
            home?.let {
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(it)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        .flowOn(ioDispatcher)

    /** Opens device settings. */
    val settingsIntent: Intent
        get() = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

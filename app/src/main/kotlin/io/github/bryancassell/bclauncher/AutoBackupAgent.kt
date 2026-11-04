package io.github.bryancassell.bclauncher

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * Declared in the manifest only so Android honors its android:backupInForeground, which it reads only for an app with
 * a backup agent. With the attribute alone, the installed app had no BACKUP_IN_FOREGROUND flag (Google TV emulator,
 * API 36). Without the flag, the nightly Auto Backup skips an app it counts as in the foreground. While
 * HomeButtonService is on, the system's binding keeps BC Launcher's uid at BOUND_FOREGROUND_SERVICE, or
 * IMPORTANT_FOREGROUND while the TV sleeps, so every nightly backup was put off ("busy; deferring"). Launcher3 declares
 * the same agent, fullBackupOnly and backupInForeground attributes (#8).
 *
 * android:fullBackupOnly has Auto Backup back up the files through BackupAgent's own onFullBackup, so the key-value
 * methods below are never called. A key-value agent with FileBackupHelper would avoid shutting the app down, but every
 * save would have to call BackupManager.dataChanged(). Each backup shuts down the main process, where the agent runs,
 * but not :home_button, so the Home button keeps working.
 *
 * The nightly backup starts only once JobScheduler counts the device as idle, which takes the screen off for a while,
 * so the home screen isn't showing then. A backup forced with the home screen showing removed it, and Google TV's home
 * took its place until Home was pressed.
 */
class AutoBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit
}

package io.github.bryancassell.bclauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bclauncher.data.InstalledApp
import io.github.bryancassell.bclauncher.data.InstalledAppsRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the home screen shows. */
sealed interface HomeUiState {
    /** The first list hasn't loaded yet. */
    data object Loading : HomeUiState

    data class Content(val apps: List<InstalledApp>) : HomeUiState

    /** No app other than BC Launcher opens from a TV's app list. */
    data object Empty : HomeUiState

    /** Loading the apps failed. */
    data object Error : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(repository: InstalledAppsRepository) : ViewModel() {
    // Each new value restarts collecting the apps, which loads them again.
    private val loadRequests = MutableStateFlow(0)

    /**
     * Stops collecting 5 seconds after the screen stops, such as while another app is open, and starts again when it
     * returns, which loads the apps again. 5 seconds outlasts the activity being recreated for a configuration change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = loadRequests
        .flatMapLatest { repository.apps }
        .map { apps -> if (apps.isEmpty()) HomeUiState.Empty else HomeUiState.Content(apps) }
        // PackageManager catches the exceptions that loading a label or an image throws, so what gets here is an
        // Error such as OutOfMemoryError (#4). Collecting again when the screen returns tries again.
        .catch { emit(HomeUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /**
     * Loads the apps again after one didn't open, since its tile may be stale: when an update removes an app's only
     * TV activity, BC Launcher can no longer see the package, so no change is reported for it (#4).
     *
     * Restarting the collection, rather than asking the repository for one more load, keeps the repository's
     * changes in a single channel, where those that arrive during a load are merged into one more load. A second
     * flow merged in would forward them one at a time.
     */
    fun onLaunchFailed() {
        loadRequests.update { it + 1 }
    }
}

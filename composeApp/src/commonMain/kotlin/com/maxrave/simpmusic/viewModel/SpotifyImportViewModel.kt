package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.repository.SpotifyImportProgress
import com.maxrave.domain.repository.SpotifyImportRepository
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.spotify_import_invalid_url
import simpmusic.composeapp.generated.resources.spotify_import_not_logged_in

/**
 * Drives an import of a Spotify playlist by URL.
 *
 * Mirrors [ImportViewModel], but there is no file to read here — the URL goes straight to the
 * repository, which does the paging and the YouTube Music matching.
 */
class SpotifyImportViewModel(
    private val spotifyImportRepository: SpotifyImportRepository,
) : BaseViewModel() {
    private val _importState: MutableStateFlow<SpotifyImportProgress?> = MutableStateFlow(null)

    /** `null` while idle; otherwise the latest step of the running or finished import. */
    val importState: StateFlow<SpotifyImportProgress?> = _importState.asStateFlow()

    private var importJob: Job? = null

    fun import(playlistUrl: String) {
        importJob?.cancel()
        importJob =
            viewModelScope.launch {
                _importState.value = SpotifyImportProgress.Preparing
                spotifyImportRepository
                    .importPlaylist(
                        playlistUrl = playlistUrl,
                        invalidUrlMessage = getString(Res.string.spotify_import_invalid_url),
                        notLoggedInMessage = getString(Res.string.spotify_import_not_logged_in),
                    ).collect { progress ->
                        _importState.value = progress
                    }
            }
    }

    /**
     * Back to idle, which is what dismisses the progress/result dialog.
     *
     * Cancelling mid-matching is safe and loses nothing the user would miss: the repository writes
     * nothing until every track has been resolved.
     */
    fun dismiss() {
        importJob?.cancel()
        importJob = null
        _importState.value = null
    }
}

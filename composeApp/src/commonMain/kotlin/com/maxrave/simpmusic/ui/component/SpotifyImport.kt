package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.repository.SpotifyImportProgress
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SpotifyImportViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.import_data
import simpmusic.composeapp.generated.resources.import_failed
import simpmusic.composeapp.generated.resources.import_result
import simpmusic.composeapp.generated.resources.ok
import simpmusic.composeapp.generated.resources.spotify_import
import simpmusic.composeapp.generated.resources.spotify_import_hint
import simpmusic.composeapp.generated.resources.spotify_import_intro
import simpmusic.composeapp.generated.resources.spotify_import_matching
import simpmusic.composeapp.generated.resources.spotify_import_pasted
import simpmusic.composeapp.generated.resources.spotify_import_reading
import simpmusic.composeapp.generated.resources.spotify_import_unmatched
import simpmusic.composeapp.generated.resources.spotify_import_writing

/**
 * The whole "import a Spotify playlist" flow, droppable anywhere.
 *
 * Owns its ViewModel and both dialogs, so a caller only decides WHEN it opens: Settings, the Jenny
 * quick-settings block, the Library screen and the first-run welcome all use this, and none of them
 * has to know how the import itself works.
 *
 * @param visible whether the link dialog is showing. The progress dialog follows the import's own
 * state instead, so it stays up after the link dialog closes.
 */
@Composable
fun SpotifyImportHost(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val viewModel: SpotifyImportViewModel = koinViewModel()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    if (visible) {
        SpotifyImportUrlDialog(
            onDismiss = onDismiss,
            onConfirm = { url ->
                onDismiss()
                viewModel.import(url)
            },
        )
    }
    importState?.let { progress ->
        SpotifyImportProgressDialog(
            progress = progress,
            onDismiss = viewModel::dismiss,
        )
    }
}

/**
 * Returns the playlist id in [input], or null when it is not a Spotify playlist link.
 *
 * Mirrors `SpotifyImportRepositoryImpl.parsePlaylistId`; that one lives in the data module, which
 * the UI does not depend on, and the two are small enough that sharing them would cost more than
 * it saves.
 */
private fun spotifyPlaylistId(input: String): String? {
    val trimmed = input.trim()
    val candidate =
        when {
            trimmed.contains("playlist/") ->
                trimmed.substringAfter("playlist/").substringBefore('?').substringBefore('/')
            trimmed.contains("playlist:") ->
                trimmed.substringAfter("playlist:").substringBefore('?')
            else -> trimmed
        }
    return candidate.takeIf { it.length == 22 && it.all { c -> c.isLetterOrDigit() } }
}

/**
 * Asks for the playlist link, pre-filled from the clipboard when it already holds one.
 *
 * The usual path is: Spotify → Share → Copy link → open this. Pre-filling turns that into a single
 * tap on Import, and it only ever fills in something that parses as a playlist, so an unrelated
 * clipboard (a password, a message) is never pasted into the field.
 */
@Composable
private fun SpotifyImportUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var url by rememberSaveable { mutableStateOf("") }
    var pasted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (url.isEmpty()) {
            val clip = runCatching { clipboard.getText()?.text }.getOrNull().orEmpty()
            if (spotifyPlaylistId(clip) != null) {
                url = clip.trim()
                pasted = true
            }
        }
    }
    val looksValid = remember(url) { spotifyPlaylistId(url) != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.spotify_import), style = typo().titleSmall) },
        text = {
            Column {
                Text(
                    text =
                        stringResource(
                            if (pasted) Res.string.spotify_import_pasted else Res.string.spotify_import_intro,
                        ),
                    style = typo().bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = url,
                    onValueChange = {
                        url = it
                        pasted = false
                    },
                    label = { Text(stringResource(Res.string.spotify_import_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = looksValid,
                onClick = { onConfirm(url.trim()) },
            ) { Text(text = stringResource(Res.string.import_data)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * Progress and outcome of an import.
 *
 * Dismissible at every stage: matching is the long phase and it writes nothing — the repository
 * only hands the resolved set to the importer once every track has been looked up — so backing out
 * of a long playlist leaves no half-built playlist behind.
 */
@Composable
private fun SpotifyImportProgressDialog(
    progress: SpotifyImportProgress,
    onDismiss: () -> Unit,
) {
    val finished = progress is SpotifyImportProgress.Success || progress is SpotifyImportProgress.Error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    stringResource(
                        if (progress is SpotifyImportProgress.Error) Res.string.import_failed else Res.string.spotify_import,
                    ),
                style = typo().titleSmall,
            )
        },
        text = {
            Column {
                when (progress) {
                    is SpotifyImportProgress.Preparing -> {
                        Text(text = stringResource(Res.string.spotify_import_reading), style = typo().bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is SpotifyImportProgress.Matching -> {
                        Text(
                            text = stringResource(Res.string.spotify_import_matching, progress.matched, progress.total),
                            style = typo().bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.matched.toFloat() / progress.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is SpotifyImportProgress.Writing -> {
                        Text(
                            text = stringResource(Res.string.spotify_import_writing, progress.processed, progress.total),
                            style = typo().bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is SpotifyImportProgress.Success -> {
                        Text(
                            text =
                                stringResource(
                                    Res.string.import_result,
                                    progress.result.playlistsCreated,
                                    progress.result.songsImported,
                                ),
                            style = typo().bodyMedium,
                        )
                        // Reported rather than hidden: YouTube Music genuinely does not carry
                        // everything Spotify does, so a short playlist is expected, not a bug.
                        if (progress.unmatched > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(Res.string.spotify_import_unmatched, progress.unmatched),
                                style = typo().bodySmall,
                            )
                        }
                    }

                    is SpotifyImportProgress.Error -> {
                        Text(text = progress.message, style = typo().bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(if (finished) Res.string.ok else Res.string.cancel))
            }
        },
    )
}

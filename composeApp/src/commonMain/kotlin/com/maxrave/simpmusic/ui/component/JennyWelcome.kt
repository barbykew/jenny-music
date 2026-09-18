package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.ui.icon.ArrowForwardIos
import com.maxrave.simpmusic.ui.icon.CheckCircle
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.login.DiscordLoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.LoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.SpotifyLoginDestination
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.circle_app_icon
import simpmusic.composeapp.generated.resources.jenny_welcome_discord
import simpmusic.composeapp.generated.resources.jenny_welcome_discord_why
import simpmusic.composeapp.generated.resources.jenny_welcome_done
import simpmusic.composeapp.generated.resources.jenny_welcome_import
import simpmusic.composeapp.generated.resources.jenny_welcome_import_locked
import simpmusic.composeapp.generated.resources.jenny_welcome_import_why
import simpmusic.composeapp.generated.resources.jenny_welcome_later
import simpmusic.composeapp.generated.resources.jenny_welcome_spotify
import simpmusic.composeapp.generated.resources.jenny_welcome_spotify_why
import simpmusic.composeapp.generated.resources.jenny_welcome_subtitle
import simpmusic.composeapp.generated.resources.jenny_welcome_title
import simpmusic.composeapp.generated.resources.jenny_welcome_youtube
import simpmusic.composeapp.generated.resources.jenny_welcome_youtube_why

/**
 * First-run welcome: every sign-in the app can use, on one card, each with its own tick.
 *
 * Without this the three logins live in three different Settings sections, and nothing says which
 * one does what — so a new user signs into none of them and then wonders why lyrics, import and
 * Discord presence all silently do nothing.
 *
 * It shows until "Let's go" or "Maybe later" is pressed, and is HIDDEN (not dismissed) while a
 * login screen is open: tapping a row navigates to that screen, and the card comes back on its own
 * when the user returns, with that row now ticked. That is why it keys off [currentRoute] rather
 * than tracking its own "went to log in" flag — the back stack already knows.
 *
 * Every tick is read live from DataStore, so a login finished anywhere — here, or in Settings
 * before this was ever seen — is reflected without the card having to be told.
 */
@Composable
fun JennyWelcome(
    navController: NavController,
    currentRoute: String?,
) {
    val dataStoreManager: DataStoreManager = koinInject()
    // Starts as "seen" on purpose: DataStore fills this asynchronously, and starting from "not
    // seen" would flash the welcome for a frame on every launch of an app already set up.
    val seen by dataStoreManager.jennyWelcomeSeen.collectAsStateWithLifecycle(DataStoreManager.TRUE)
    val cookie by dataStoreManager.cookie.collectAsStateWithLifecycle("")
    val spdc by dataStoreManager.spdc.collectAsStateWithLifecycle("")
    val discordToken by dataStoreManager.discordToken.collectAsStateWithLifecycle("")
    val scope = rememberCoroutineScope()
    var showImport by rememberSaveable { mutableStateOf(false) }

    // The import flow outlives the card: it is started from here but finishes after "Let's go".
    SpotifyImportHost(visible = showImport, onDismiss = { showImport = false })

    if (seen == DataStoreManager.TRUE) return
    // Stand aside while a login screen is up; reappear when the user comes back.
    if (currentRoute?.contains(".login.") == true) return

    val youtubeDone = cookie.isNotEmpty()
    val spotifyDone = spdc.isNotEmpty()
    val discordDone = discordToken.isNotEmpty()
    val finish: () -> Unit = { scope.launch { dataStoreManager.setJennyWelcomeSeen(true) } }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(20.dp).widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The app's own spider icon rather than an emoji: Skia on Windows has no colour
                // emoji font to fall back to, so a spider emoji would draw as an empty box there.
                Image(
                    painter = painterResource(Res.drawable.circle_app_icon),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.jenny_welcome_title),
                    style = typo().titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.jenny_welcome_subtitle),
                    style = typo().bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                WelcomeStep(
                    title = stringResource(Res.string.jenny_welcome_youtube),
                    why = stringResource(Res.string.jenny_welcome_youtube_why),
                    done = youtubeDone,
                    onClick = { navController.navigate(LoginDestination) },
                )
                WelcomeStep(
                    title = stringResource(Res.string.jenny_welcome_spotify),
                    why = stringResource(Res.string.jenny_welcome_spotify_why),
                    done = spotifyDone,
                    onClick = { navController.navigate(SpotifyLoginDestination) },
                )
                WelcomeStep(
                    title = stringResource(Res.string.jenny_welcome_discord),
                    why = stringResource(Res.string.jenny_welcome_discord_why),
                    done = discordDone,
                    onClick = { navController.navigate(DiscordLoginDestination) },
                )
                // Import needs a Spotify session, so it stays visible but explains itself instead of
                // failing with "log in first" after the user has already pasted a link.
                WelcomeStep(
                    title = stringResource(Res.string.jenny_welcome_import),
                    why =
                        stringResource(
                            if (spotifyDone) Res.string.jenny_welcome_import_why else Res.string.jenny_welcome_import_locked,
                        ),
                    done = false,
                    enabled = spotifyDone,
                    onClick = { showImport = true },
                )

                Spacer(Modifier.height(20.dp))
                Button(onClick = finish, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(Res.string.jenny_welcome_done), fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = finish) {
                    Text(stringResource(Res.string.jenny_welcome_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    title: String,
    why: String,
    done: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container =
        if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .clickable(enabled = enabled && !done, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo().titleSmall,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
            Text(
                text = why,
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f),
            )
        }
        if (done) {
            Icon(SimpIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else if (enabled) {
            Icon(
                SimpIcons.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

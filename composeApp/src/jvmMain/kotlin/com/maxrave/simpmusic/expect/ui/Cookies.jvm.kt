package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.ui.theme.typo
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.callback.CefJSDialogCallback
import org.cef.handler.CefJSDialogHandler
import org.cef.handler.CefJSDialogHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookieManager
import java.awt.BorderLayout
import java.io.File
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

private const val TAG = "DesktopWebView"

/**
 * Embedded Chromium for the desktop login screens.
 *
 * Android gets a real `android.webkit.WebView`; Compose Multiplatform ships nothing equivalent on
 * the JVM, so upstream's actual was a placeholder that sent the user to a blog post. This replaces
 * it with CEF (Chromium Embedded Framework) through KCEF, so Discord, Spotify and YouTube log in
 * on desktop exactly as they do on the phone.
 *
 * CEF's native bundle is ~150 MB and is NOT in the jar — KCEF downloads it on first use into
 * [cefInstallDir] and reuses it forever after. That download is the reason every entry point here
 * reports progress through [WebViewState.Loading] rather than blocking on a blank screen.
 */
private val cefInstallDir: File
    get() = File(System.getProperty("user.home"), ".jennymusic/kcef-bundle")

/**
 * CEF's helper binary, whose name differs per OS: a bare `jcef_helper` on Linux, `jcef_helper.exe`
 * on Windows, and a nested .app bundle on macOS. Only the Windows build ships today, but this file
 * compiles for all three, so the wrong name here would move the crash rather than remove it.
 */
private fun cefHelperExecutable(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("win") -> File(cefInstallDir, "jcef_helper.exe")
        os.contains("mac") || os.contains("darwin") ->
            File(cefInstallDir, "jcef Helper.app/Contents/MacOS/jcef Helper")
        else -> File(cefInstallDir, "jcef_helper")
    }
}

/**
 * Initialisation is process-wide and must happen exactly once, so it is cached here rather than
 * tied to a composable's lifetime — reopening the login screen must not restart a 150 MB download,
 * and CEF itself throws if initialised twice.
 */
private object CefRuntime {
    @Volatile
    private var client: KCEFClient? = null

    @Volatile
    private var failure: String? = null

    /** 0..100 while the native bundle downloads; null once CEF is up. */
    @Volatile
    var downloadPercent: Float? = null
        private set

    val lastError: String? get() = failure

    @Synchronized
    private fun initIfNeeded(): KCEFClient? {
        client?.let { return it }
        failure?.let { return null }
        return runCatching {
            KCEF.initBlocking(
                builder = {
                    installDir(cefInstallDir)
                    progress {
                        onDownloading { percent -> downloadPercent = percent }
                        onInitialized { downloadPercent = null }
                    }
                    settings {
                        // Keep the login session on disk so a token survives an app restart, the
                        // same way the Android WebView's cookie jar does.
                        cachePath = File(cefInstallDir, "cache").absolutePath

                        // MUST be set, or CEF re-launches THIS executable for each of its helper
                        // processes (gpu, utility, renderer) and passes Chromium switches to it.
                        // Under a Java launcher the JVM then receives "--type=gpu-process", prints
                        // "Unrecognized option" and aborts — repeatedly, until the whole app dies.
                        // That is a silent native death: no Kotlin exception, no hs_err file, the
                        // process simply disappears a second after the login screen opens.
                        browserSubProcessPath = cefHelperExecutable().absolutePath
                        resourcesDirPath = cefInstallDir.absolutePath
                        localesDirPath = File(cefInstallDir, "locales").absolutePath
                    }
                },
                onError = { t ->
                    failure = t?.message ?: "CEF failed to start"
                    Logger.e(TAG, "KCEF init error: ${t?.message}")
                },
                onRestartRequired = {
                    // CEF on some platforms needs the JVM restarted after it unpacks itself. This
                    // is reported rather than retried: retrying in-process cannot work.
                    failure = "Chromium finished installing. Please restart Jenny Music and log in again."
                },
            )
            KCEF.newClientBlocking().also { client = it }
        }.onFailure {
            failure = it.message ?: it::class.simpleName
            Logger.e(TAG, "KCEF init threw: ${it.message}")
        }.getOrNull()
    }

    suspend fun client(): KCEFClient? = withContext(Dispatchers.IO) { initIfNeeded() }
}

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        /**
         * Reads CEF's cookie jar, which is where a desktop login actually lands — the old
         * implementation read `java.net.CookieHandler`, a store nothing in this app ever writes,
         * so it always returned "" and YouTube/Spotify sign-in could not work even in principle.
         */
        override fun getCookie(url: String): String =
            runCatching {
                val manager = CefCookieManager.getGlobalManager() ?: return@runCatching ""
                dev.datlag.kcef.KCEFCookieManager(manager)
                    .getCookiesWhileBlocking(url, true, TimeUnit.SECONDS.toMillis(5)) { _, _ -> true }
                    .joinToString("; ") { "${it.name}=${it.value}" }
            }.onFailure { Logger.e(TAG, "getCookie failed: ${it.message}") }
                .getOrDefault("")

        override fun removeAllCookies() {
            runCatching {
                CefCookieManager.getGlobalManager()?.deleteCookies(null, null)
            }.onFailure { Logger.e(TAG, "removeAllCookies failed: ${it.message}") }
        }
    }

/**
 * Hosts a [KCEFBrowser] and reports load progress.
 *
 * The browser is created off the UI thread (CEF blocks while it starts) and is disposed when the
 * composable leaves, otherwise each visit to a login screen leaks a Chromium process.
 */
/**
 * Opens the CEF browser in its own top-level window and reports status inside the app.
 *
 * The browser deliberately does NOT live inside the Compose window. Three separate failures forced
 * that, each hidden behind the previous one:
 *
 *  1. The app's window is `transparent = true, undecorated = true` (DesktopApp.kt). Java cannot
 *     composite a HEAVYWEIGHT AWT child inside a translucent window, so CEF's default canvas
 *     punched a hole through the app and showed the desktop behind it.
 *  2. Switching to OFFSCREEN rendering avoided that, but JCEF's offscreen path draws through
 *     OpenGL, which needs JOGL's natives — and then fails outright on a 10-bit/HDR display, where
 *     JOGL asks for `rgba 8/8/8/0`, Windows offers `rgba 10/10/10/2`, and JOGL rejects the
 *     mismatch with "Unable to determine GraphicsConfiguration". That depends on the user's
 *     monitor, so it cannot be relied on.
 *  3. A plain opaque [JFrame] has neither problem: default rendering, no translucency to fight,
 *     no OpenGL anywhere. It is also how desktop apps normally present a sign-in.
 *
 * The frame is disposed when the composable leaves, so closing the login screen cannot leave an
 * orphaned Chromium window behind.
 */
@Composable
private fun CefHost(
    state: MutableState<WebViewState>,
    url: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    windowTitle: String,
    configure: (KCEFClient) -> Unit = {},
    onLoadEnd: (CefBrowser, String) -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var percent by remember { mutableStateOf<Float?>(null) }
    var frame by remember { mutableStateOf<JFrame?>(null) }
    var browser by remember { mutableStateOf<KCEFBrowser?>(null) }
    val currentOnLoadEnd by rememberUpdatedState(onLoadEnd)
    val currentConfigure by rememberUpdatedState(configure)

    LaunchedEffect(url) {
        // Poll the download percentage WHILE init runs. Sampling it once after CefRuntime.client()
        // returns is useless: that call blocks for the whole download, so by the time it comes back
        // the percentage is already null and the user has watched a bar with no number on it for
        // several minutes of a ~600 MB fetch, with nothing to distinguish it from a hang.
        val progressPoll = launch {
            while (isActive) {
                percent = CefRuntime.downloadPercent
                delay(200)
            }
        }
        val client = try {
            CefRuntime.client()
        } finally {
            progressPoll.cancel()
        }
        percent = null
        if (client == null) {
            error = CefRuntime.lastError ?: "Could not start the browser"
            return@LaunchedEffect
        }
        currentConfigure(client)
        // Discord signs in with XHR and then routes CLIENT-SIDE to /channels/@me — no new document
        // is loaded, so onLoadEnd never fires again and a check that lives only there never runs.
        // onAddressChange does fire for SPA navigation, which is what actually catches the login.
        // Both are wired up: a normal page load reports through onLoadEnd, a route change through
        // onAddressChange, and the callback itself is idempotent.
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(
                    b: CefBrowser?,
                    f: CefFrame?,
                    url: String?,
                ) {
                    if (f?.isMain != true) return
                    val current = url ?: return
                    b?.let { currentOnLoadEnd(it, current) }
                }
            },
        )
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    b: CefBrowser?,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    state.value = if (isLoading) WebViewState.Loading(50) else WebViewState.Finished
                }

                override fun onLoadEnd(
                    b: CefBrowser?,
                    f: CefFrame?,
                    httpStatusCode: Int,
                ) {
                    // Main frame only: a page like Discord's loads dozens of subframes, and acting
                    // on each would run the login check once per frame.
                    if (f?.isMain != true) return
                    val current = b?.url ?: return
                    currentOnLoadEnd(b, current)
                }
            },
        )

        val created = withContext(Dispatchers.IO) {
            runCatching { client.createBrowser(url, CefRendering.DEFAULT, false) }
                .onFailure {
                    error = it.message
                    Logger.e(TAG, "createBrowser failed: ${it.message}")
                }.getOrNull()
        } ?: return@LaunchedEffect
        browser = created

        withContext(Dispatchers.Main) {
            frame = JFrame(windowTitle).apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                contentPane.add(created.uiComponent, BorderLayout.CENTER)
                setSize(1000, 760)
                setLocationRelativeTo(null)
                isVisible = true
                toFront()
                requestFocus()
            }
            status = "A sign-in window has opened. Finish logging in there — this screen will close by itself."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { browser?.close(true) }
            frame?.let { f -> SwingUtilities.invokeLater { f.dispose() } }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = error
                    ?: status
                    ?: percent?.let { "Downloading browser… ${it.toInt()}% — one-time ~600 MB download" }
                    ?: "Starting browser…",
                style = typo().labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (error == null && status == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        }
        aboveContent()
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    CefHost(
        state = state,
        url = initUrl,
        aboveContent = aboveContent,
        windowTitle = "Jenny Music — Sign in",
        onLoadEnd = { _, url -> onPageFinished(url) },
    )
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (String) -> Unit,
) {
    val currentOnLoginDone by rememberUpdatedState(onLoginDone)
    // The token read is retried, so the alert can arrive several times; only the first may log in.
    val delivered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    CefHost(
        state = state,
        url = "https://discord.com/login",
        aboveContent = aboveContent,
        windowTitle = "Jenny Music — Sign in to Discord",
        configure = { client ->
            // Same trick the Android actual uses: the injected script reads localStorage.token and
            // hands it out through alert(), which is intercepted here instead of being shown.
            // Discord's own page scripts delete `localStorage` off the window, so the value has to
            // be read through a freshly created iframe's window — that is what the snippet does.
            client.addJSDialogHandler(
                object : CefJSDialogHandlerAdapter() {
                    override fun onJSDialog(
                        browser: CefBrowser?,
                        originUrl: String?,
                        dialogType: CefJSDialogHandler.JSDialogType?,
                        messageText: String?,
                        defaultPromptText: String?,
                        callback: CefJSDialogCallback?,
                        suppressMessage: BoolRef?,
                    ): Boolean {
                        val token = messageText?.takeIf { it.isNotBlank() }
                        callback?.Continue(true, "")
                        suppressMessage?.set(true)
                        // onJSDialog runs on a CEF thread. The callback saves the token and
                        // navigates back, and navigation must happen on the UI thread — calling it
                        // from here threw "setCurrentState must be called on the main thread" and
                        // the login was lost even though the token had been read correctly.
                        if (token != null && delivered.compareAndSet(false, true)) {
                            SwingUtilities.invokeLater { currentOnLoginDone(token) }
                        }
                        return true
                    }
                },
            )
        },
        onLoadEnd = { browser, url ->
            // Reaching /app or /channels means the session exists. The script is fired on a short
            // repeat because the route change is announced before Discord has finished writing the
            // token into localStorage — a single shot right on navigation reads nothing and the
            // window then sits there logged in but never closing. The snippet is a no-op once the
            // token has already been handed over, so repeating it is harmless.
            if (url.contains("/app") || url.contains("/channels")) {
                repeat(TOKEN_READ_ATTEMPTS) { attempt ->
                    Timer(true).schedule(
                        object : TimerTask() {
                            override fun run() {
                                runCatching { browser.executeJavaScript(DISCORD_TOKEN_SNIPPET, url, 0) }
                            }
                        },
                        attempt * TOKEN_READ_INTERVAL_MS,
                    )
                }
            }
        },
    )
}

/**
 * Reads the Discord token out of localStorage and surfaces it via `alert`.
 *
 * Written as plain JS rather than the `javascript:`-URL form the Android actual uses, because
 * [KCEFBrowser.executeJavaScript] takes a script body, not a navigation URL.
 */
private const val TOKEN_READ_ATTEMPTS = 8
private const val TOKEN_READ_INTERVAL_MS = 700L

private const val DISCORD_TOKEN_SNIPPET = """
    (function () {
        var i = document.createElement('iframe');
        document.body.appendChild(i);
        var t = i.contentWindow.localStorage.token;
        i.remove();
        if (t) { alert(t.slice(1, -1)); }
    })();
"""

package app.ammusic.ui.account

import app.ammusic.youtube.tr

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import app.ammusic.ui.AccountViewModel
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val LoginUrl = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val MusicUrl = "https://music.youtube.com"

private const val ConfigScript =
    "(function(){var c=window.yt&&window.yt.config_;return c&&c.VISITOR_DATA?JSON.stringify({v:c.VISITOR_DATA,d:c.DATASYNC_ID||null}):null})()"

private const val MaxAttempts = 12

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val actions = LocalActions.current
    val colors = AmTheme.colors
    var loading by remember { mutableStateOf(true) }
    var signedIn by remember { mutableStateOf(false) }
    var arrived by remember { mutableStateOf(false) }
    var collecting by remember { mutableStateOf(false) }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(colors.background).padding(top = statusBar).navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            IconButton(AmIcons.Close, label = tr("Fermer", "Close"), onClick = actions.back, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            AmText(tr("Connexion", "Sign in"), style = AmTheme.type.headline, modifier = Modifier.align(Alignment.Center))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                if (url.isMusicSite()) arrived = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                if (signedIn || collecting || !url.isMusicSite()) return
                                collecting = true
                                collect(
                                    view = view,
                                    attempt = 0,
                                    onSession = { session ->
                                        signedIn = true
                                        viewModel.signIn(session, onDone = actions.back)
                                    },
                                    onMissing = {
                                        collecting = false
                                        arrived = false
                                        view.loadUrl(LoginUrl)
                                    },
                                )
                            }
                        }
                        loadUrl(LoginUrl)
                    }
                },
                onRelease = { it.destroy() },
            )
            if (loading || arrived) {
                Column(Modifier.fillMaxSize().background(colors.background)) {
                    if (arrived) {
                        AmText(
                            text = tr("Connexion réussie", "Signed in"),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        )
                    }
                    RowsSkeleton(6, Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

private fun collect(view: WebView, attempt: Int, onSession: (YtSession) -> Unit, onMissing: () -> Unit) {
    val retry = { view.postDelayed({ collect(view, attempt + 1, onSession, onMissing) }, 350) }
    val cookie = CookieManager.getInstance().getCookie(MusicUrl).orEmpty()
    if (cookie.split("; ").none { it.startsWith("SAPISID=") }) {
        if (attempt < MaxAttempts) retry() else onMissing()
        return
    }
    view.evaluateJavascript(ConfigScript) { raw ->
        val config = runCatching { Json.parseToJsonElement(Json.decodeFromString<String>(raw)).jsonObject }.getOrNull()
        when {
            config != null -> onSession(
                YtSession(cookie, config["v"]?.jsonPrimitive?.contentOrNull, config["d"]?.jsonPrimitive?.contentOrNull),
            )
            attempt < MaxAttempts -> retry()
            else -> onSession(YtSession(cookie, null, null))
        }
    }
}

private fun String?.isMusicSite(): Boolean {
    val uri = this?.toUri() ?: return false
    return uri.scheme == "https" && uri.host == "music.youtube.com"
}

package app.ammusic.ui.account

import android.annotation.SuppressLint
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

/** Lit dans la page les deux identifiants que YouTube Music attend en plus des cookies. */
private const val ConfigScript =
    "(function(){var c=window.yt&&window.yt.config_;return c&&c.VISITOR_DATA?JSON.stringify({v:c.VISITOR_DATA,d:c.DATASYNC_ID||null}):null})()"

private const val MaxAttempts = 12

/**
 * Connexion au compte : la page de Google s'affiche dans une fenêtre web, l'appli ne voit jamais le mot de passe.
 * Une fois arrivé sur music.youtube.com, elle récupère les cookies de la session et referme l'écran.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val actions = LocalActions.current
    val colors = AmTheme.colors
    var loading by remember { mutableStateOf(true) }
    var signedIn by remember { mutableStateOf(false) }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(colors.background).padding(top = statusBar).navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            IconButton(AmIcons.Close, label = "Fermer", onClick = actions.back, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            AmText("Connexion", style = AmTheme.type.headline, modifier = Modifier.align(Alignment.Center))
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
                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                val uri = url?.toUri() ?: return
                                if (signedIn || uri.scheme != "https" || uri.host != "music.youtube.com") return
                                collect(view, attempt = 0) { session ->
                                    if (!signedIn) {
                                        signedIn = true
                                        viewModel.signIn(session, onDone = actions.back)
                                    }
                                }
                            }
                        }
                        loadUrl(LoginUrl)
                    }
                },
                onRelease = { it.destroy() },
            )
            if (loading || signedIn) {
                Column(Modifier.fillMaxSize().background(colors.background)) {
                    if (signedIn) {
                        AmText(
                            text = "Connexion réussie. Récupération de votre bibliothèque…",
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

/**
 * Attend que la session soit complète : le cookie `SAPISID` (preuve de connexion) et les identifiants de la page,
 * qui n'apparaissent qu'un instant après la fin du chargement. Sans eux au bout de [MaxAttempts] essais, on se contente des cookies.
 */
private fun collect(view: WebView, attempt: Int, onSession: (YtSession) -> Unit) {
    val cookie = CookieManager.getInstance().getCookie(MusicUrl).orEmpty()
    if (cookie.split("; ").none { it.startsWith("SAPISID=") }) return
    view.evaluateJavascript(ConfigScript) { raw ->
        val config = runCatching { Json.parseToJsonElement(Json.decodeFromString<String>(raw)).jsonObject }.getOrNull()
        when {
            config != null -> onSession(
                YtSession(cookie, config["v"]?.jsonPrimitive?.contentOrNull, config["d"]?.jsonPrimitive?.contentOrNull),
            )
            attempt < MaxAttempts -> view.postDelayed({ collect(view, attempt + 1, onSession) }, 350)
            else -> onSession(YtSession(cookie, null, null))
        }
    }
}

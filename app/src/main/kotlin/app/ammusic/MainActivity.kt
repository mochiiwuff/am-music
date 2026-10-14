package app.ammusic

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.data.UserSettings
import app.ammusic.ui.AppRoot
import app.ammusic.ui.theme.AccentChoices
import app.ammusic.ui.theme.AmTheme
import app.ammusic.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { splash ->
                splash.iconView?.animate()?.scaleX(1.18f)?.scaleY(1.18f)?.setDuration(260)?.setInterpolator(DecelerateInterpolator(2f))?.start()
                splash.animate().alpha(0f).setStartDelay(60).setDuration(240).withEndAction { splash.remove() }.start()
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by UserSettings.state.collectAsStateWithLifecycle()
            val themeMode = ThemeMode.entries[settings.theme.coerceIn(0, ThemeMode.entries.lastIndex)]
            val accentIndex = settings.accent.coerceIn(0, AccentChoices.lastIndex)
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }

            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            AmTheme(dark = dark, accent = AccentChoices[accentIndex]) {
                AppRoot(
                    themeMode = themeMode,
                    onThemeMode = { mode -> UserSettings.update { copy(theme = mode.ordinal) } },
                    accentIndex = accentIndex,
                    onAccent = { index -> UserSettings.update { copy(accent = index) } },
                )
            }
        }
    }
}

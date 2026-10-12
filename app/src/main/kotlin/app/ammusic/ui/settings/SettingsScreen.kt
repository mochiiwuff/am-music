package app.ammusic.ui.settings

import app.ammusic.youtube.tr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.data.CacheChoicesMb
import app.ammusic.playback.SleepTimer
import app.ammusic.ui.AccountViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.MenuAction
import app.ammusic.ui.components.MenuRequest
import app.ammusic.ui.components.RowCoverSize
import app.ammusic.ui.components.hairlineBelow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.slidingPill
import app.ammusic.ui.theme.AccentChoices
import app.ammusic.ui.theme.AmTheme
import app.ammusic.ui.theme.ThemeMode
import app.ammusic.youtube.model.YtAccount
import app.ammusic.youtube.stream.AudioQuality
import kotlinx.coroutines.delay

private val ThemeLabels get() = listOf(tr("Système", "System"), tr("Sombre", "Dark"), tr("Clair", "Light"))
private val LanguageCodes = listOf("", "fr", "en")
private val LanguageLabels get() = listOf(tr("Système", "System"), "Français", "English")
private val QualityLabels get() = listOf("Auto", tr("Haute", "High"), tr("Éco", "Saver"))
private val SizeLabels get() = listOf(tr("Petit", "Small"), tr("Moyen", "Medium"), tr("Grand", "Large"))
private val SleepMinutes = listOf(null, 15, 30, 60)
private val SleepLabels get() = listOf(tr("Non", "Off"), "15 min", "30 min", "1 h")

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = AmTheme.colors
    val context = LocalContext.current
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sleep by viewModel.sleep.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()

    LargeTitleScreen(
        title = tr("Réglages", "Settings"),
        leading = { IconButton(AmIcons.ChevronLeft, label = tr("Retour", "Back"), onClick = onBack, tint = colors.accent, iconSize = 26.dp) },
    ) {
        item(key = "account") { AccountCard(account, onSignOut = accountViewModel::signOut) }

        item(key = "look") {
            Group(header = tr("Apparence", "Appearance")) {
                Choice(tr("Thème", "Theme"), ThemeLabels, themeMode.ordinal) { onThemeMode(ThemeMode.entries[it]) }
                Divider()
                AccentPicker(accentIndex, onAccent)
                Divider()
                Choice(tr("Langue de l'appli", "App language"), LanguageLabels, LanguageCodes.indexOf(settings.language).coerceAtLeast(0)) { index ->
                    if (LanguageCodes[index] != settings.language) {
                        viewModel.update { copy(language = LanguageCodes[index]) }
                        context.findActivity()?.recreate()
                    }
                }
            }
        }

        item(key = "playback") {
            Group(
                header = tr("Lecture", "Playback"),
                footer = tr(
                    "« Haute » sonne mieux, « Éco » consomme moins de données. Le changement s'applique au titre suivant.",
                    "“High” sounds better, “Saver” uses less data. The change applies from the next song.",
                ),
            ) {
                Choice(tr("Qualité en Wi-Fi", "Quality on Wi-Fi"), QualityLabels, settings.wifiQuality.ordinal) {
                    viewModel.update { copy(wifiQuality = AudioQuality.entries[it]) }
                }
                Divider()
                Choice(tr("Qualité en données mobiles", "Quality on mobile data"), QualityLabels, settings.mobileQuality.ordinal) {
                    viewModel.update { copy(mobileQuality = AudioQuality.entries[it]) }
                }
                Divider()
                Toggle(tr("Enchaîner avec des titres similaires", "Keep playing similar songs"), settings.autoplay) {
                    viewModel.update { copy(autoplay = it) }
                }
            }
        }

        item(key = "skip") {
            Group(
                footer = tr(
                    "Saute les intros parlées et les passages sans musique des clips. L'appli interroge pour cela le service SponsorBlock, sans lui donner le titre exact.",
                    "Skips spoken intros and non-music parts of music videos. The app asks the SponsorBlock service for this, without telling it the exact song.",
                ),
            ) {
                Toggle(tr("Passer les passages sans musique", "Skip non-music parts"), settings.skipSegments) {
                    viewModel.update { copy(skipSegments = it) }
                }
            }
        }

        item(key = "sleep") {
            Group(header = tr("Minuteur de sommeil", "Sleep timer"), footer = sleepStatus(sleep)) {
                Choice(tr("Arrêter la musique dans", "Stop the music in"), SleepLabels, SleepMinutes.indexOf(sleep.minutes).coerceAtLeast(0)) {
                    viewModel.sleepIn(SleepMinutes[it])
                }
                Divider()
                Toggle(tr("Arrêter à la fin du titre", "Stop when the song ends"), sleep.endOfTrack, viewModel::sleepAtEndOfTrack)
            }
        }

        item(key = "lyrics") {
            Group(header = tr("Paroles", "Lyrics")) {
                Choice(tr("Taille du texte", "Text size"), SizeLabels, settings.lyricsSize) { viewModel.update { copy(lyricsSize = it) } }
                Divider()
                Toggle(tr("Suivre les paroles mot à mot", "Follow lyrics word by word"), settings.wordByWord) {
                    viewModel.update { copy(wordByWord = it) }
                }
            }
        }

        item(key = "history") {
            Group(
                header = tr("Historique", "History"),
                footer = tr("En pause, les titres écoutés ne sont plus ajoutés à l'historique.", "While paused, songs you play are no longer added to your history."),
            ) {
                Toggle(tr("Mettre l'historique en pause", "Pause history"), settings.historyPaused) { viewModel.update { copy(historyPaused = it) } }
                Divider()
                Action(tr("Effacer l'historique", "Clear history")) { viewModel.clearHistory() }
            }
        }

        item(key = "storage") {
            Group(
                header = tr("Stockage", "Storage"),
                footer = tr(
                    "Le cache garde les titres déjà écoutés pour les relancer plus vite. Une nouvelle limite s'applique au prochain démarrage de l'appli.",
                    "The cache keeps songs you already played so they start faster. A new limit applies the next time the app starts.",
                ),
            ) {
                Choice(tr("Limite du cache audio", "Audio cache limit"), CacheChoicesMb.map(::megabytes), CacheChoicesMb.indexOf(settings.cacheMb).coerceAtLeast(0)) {
                    viewModel.update { copy(cacheMb = CacheChoicesMb[it]) }
                }
                Divider()
                Value(tr("Espace utilisé", "Space used"), megabytes((cacheBytes / (1024 * 1024)).toInt()))
                Divider()
                Action(tr("Vider le cache", "Clear cache")) { viewModel.clearCache() }
            }
        }

        item(key = "version") {
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
            AmText(
                text = "AM Music $version",
                style = AmTheme.type.footnote,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
            )
        }
    }
}

private fun megabytes(value: Int): String = when {
    value >= 1024 -> "${(value / 102.4f).toInt() / 10f}".removeSuffix(".0") + tr(" Go", " GB")
    else -> "$value" + tr(" Mo", " MB")
}

@Composable
private fun sleepStatus(sleep: SleepTimer): String? {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep) {
        while (sleep.endsAtMs != null) {
            now.longValue = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val end = sleep.endsAtMs
    return when {
        sleep.endOfTrack -> tr("La musique s'arrêtera à la fin de ce titre.", "The music will stop when this song ends.")
        end != null -> {
            val minutes = ((end - now.longValue + 59_999) / 60_000).coerceAtLeast(1)
            tr("La musique s'arrêtera en douceur dans $minutes min.", "The music will fade out in $minutes min.")
        }
        else -> null
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun Group(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = AmTheme.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = if (header != null) 26.dp else 18.dp)) {
        if (header != null) {
            AmText(
                text = header.uppercase(),
                style = AmTheme.type.footnote.copy(letterSpacing = 0.04.em),
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface), content = content)
        if (footer != null) {
            AmText(
                text = footer,
                style = AmTheme.type.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).hairlineBelow(AmTheme.colors.hairline, 16.dp))
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 14.dp)) {
        AmText(label, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Segmented(options, selected, onSelect)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = AmTheme.colors
    val position = animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = 460f), label = "segment")
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.text.copy(alpha = 0.08f))
            .slidingPill({ position.value }, options.size, if (colors.isDark) Color.White.copy(alpha = 0.17f) else Color.White, corner = 8.dp, inset = 2.dp),
    ) {
        options.forEachIndexed { index, option ->
            Box(
                Modifier.weight(1f).fillMaxHeight().pressable(scale = 0.96f, haptic = true, label = option) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                AmText(
                    text = option,
                    style = AmTheme.type.footnote.copy(fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (index == selected) colors.text else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AmTheme.colors
    val knob by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 520f), label = "switch")
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(scale = 0.99f, haptic = true, label = label) { onChange(!checked) }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(label, maxLines = 2, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Canvas(Modifier.size(width = 51.dp, height = 31.dp)) {
            val on = knob.coerceIn(0f, 1f)
            drawRoundRect(lerp(colors.text.copy(alpha = 0.16f), colors.accent, on), cornerRadius = CornerRadius(size.height / 2f))
            val gap = 2.dp.toPx()
            val radius = size.height / 2f - gap
            val x = gap + radius + (size.width - 2f * (gap + radius)) * knob
            drawCircle(Color(0x33000000), radius, Offset(x, size.height / 2f + 1.dp.toPx()))
            drawCircle(Color.White, radius, Offset(x, size.height / 2f))
        }
    }
}

@Composable
private fun Value(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AmText(label, maxLines = 1, modifier = Modifier.weight(1f))
        AmText(value, color = AmTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().pressable(scale = 0.99f, haptic = true, label = label, onClick = onClick).heightIn(min = 50.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AmText(label, color = AmTheme.colors.accent, maxLines = 1)
    }
}

@Composable
private fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 8.dp)) {
        AmText(tr("Couleur d'accent", "Accent color"), maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(start = 8.dp, top = 4.dp)) {
            AccentChoices.forEachIndexed { index, color ->
                val ring by animateFloatAsState(
                    targetValue = if (index == selected) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
                    label = "accentRing",
                )
                Box(
                    Modifier.size(48.dp).pressable(scale = 0.85f, haptic = true, label = tr("Couleur ${index + 1}", "Color ${index + 1}")) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                alpha = ring
                                scaleX = 0.8f + 0.2f * ring
                                scaleY = scaleX
                            }
                            .border(2.dp, color, CircleShape),
                    )
                    Box(Modifier.size(28.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: YtAccount?, onSignOut: () -> Unit) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val confirmSignOut = {
        actions.sheet(
            MenuRequest(
                title = account?.name.orEmpty(),
                subtitle = tr("Votre bibliothèque restera sur votre compte.", "Your library will stay on your account."),
                thumbnail = account?.avatar,
                actions = listOf(MenuAction(AmIcons.Close, tr("Se déconnecter", "Sign out"), destructive = true, onClick = onSignOut)),
            ),
        )
    }
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .fillMaxWidth()
            .pressable(scale = 0.98f, label = if (account == null) tr("Se connecter", "Sign in") else tr("Se déconnecter", "Sign out")) {
                if (account == null) actions.openLogin() else confirmSignOut()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account?.avatar != null) {
            Cover(account.avatar, Modifier.size(56.dp), circle = true, requestSize = RowCoverSize)
        } else {
            Box(Modifier.size(56.dp).clip(CircleShape).background(colors.surfaceHigh), contentAlignment = Alignment.Center) {
                AmIcon(AmIcons.User, tint = colors.accent, size = 26.dp)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            AmText(account?.name ?: tr("Se connecter", "Sign in"), style = AmTheme.type.section.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            AmText(
                text = when {
                    account == null -> tr("Vos titres aimés, playlists et recommandations YouTube Music.", "Your YouTube Music liked songs, playlists and recommendations.")
                    else -> account.email ?: tr("Compte YouTube Music", "YouTube Music account")
                },
                style = AmTheme.type.footnote,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
        if (account == null) {
            AmIcon(AmIcons.ChevronRight, tint = colors.textTertiary, size = 15.dp)
        } else {
            AmText(tr("Déconnexion", "Sign out"), style = AmTheme.type.subhead, color = colors.accent)
        }
    }
}

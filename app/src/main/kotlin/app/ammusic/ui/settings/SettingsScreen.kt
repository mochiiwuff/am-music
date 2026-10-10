package app.ammusic.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.AccountViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.MenuAction
import app.ammusic.ui.components.MenuRequest
import app.ammusic.ui.components.RowCoverSize
import app.ammusic.youtube.model.YtAccount
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.slidingPill
import app.ammusic.ui.theme.AccentChoices
import app.ammusic.ui.theme.AmTheme
import app.ammusic.ui.theme.ThemeMode

private val ThemeLabels = mapOf(ThemeMode.System to "Système", ThemeMode.Dark to "Sombre", ThemeMode.Light to "Clair")

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val colors = AmTheme.colors
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    LargeTitleScreen(
        title = "Réglages",
        leading = { IconButton(AmIcons.ChevronLeft, label = "Retour", onClick = onBack, tint = colors.accent, iconSize = 26.dp) },
    ) {
        item(key = "account") { AccountCard(account, onSignOut = accountViewModel::signOut) }
        item(key = "themeLabel") { Label("Thème") }
        item(key = "theme") { ThemePicker(themeMode, onThemeMode) }
        item(key = "accentLabel") { Label("Couleur d'accent") }
        item(key = "accent") { AccentPicker(accentIndex, onAccent) }
        item(key = "note") {
            AmText(
                text = "Qualité audio, minuteur de sommeil et cache arriveront avec la phase 6.",
                style = AmTheme.type.footnote,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    AmText(
        text = text,
        style = AmTheme.type.headline,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
    )
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = AmTheme.colors
    val position = animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "themePill",
    )
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .slidingPill({ position.value }, ThemeMode.entries.size, colors.surfaceHigh, corner = 10.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            Box(
                Modifier.weight(1f).fillMaxHeight().pressable(haptic = true, label = ThemeLabels[mode]) { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                AmText(
                    text = ThemeLabels.getValue(mode),
                    style = AmTheme.type.subhead,
                    color = if (mode == selected) colors.text else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    val colors = AmTheme.colors
    Row(Modifier.padding(horizontal = 12.dp)) {
        AccentChoices.forEachIndexed { index, color ->
            val ring by animateFloatAsState(
                targetValue = if (index == selected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
                label = "accentRing",
            )
            Box(
                Modifier.size(52.dp).pressable(scale = 0.85f, haptic = true, label = "Couleur ${index + 1}") { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .graphicsLayer { alpha = ring; scaleX = 0.8f + 0.2f * ring; scaleY = 0.8f + 0.2f * ring }
                        .border(2.dp, color, CircleShape),
                )
                Box(Modifier.size(32.dp).clip(CircleShape).background(color))
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
                subtitle = "Votre bibliothèque restera sur votre compte.",
                thumbnail = account?.avatar,
                actions = listOf(MenuAction(AmIcons.Close, "Se déconnecter", destructive = true, onClick = onSignOut)),
            ),
        )
    }
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .fillMaxWidth()
            .pressable(scale = 0.98f, label = if (account == null) "Se connecter" else "Se déconnecter") {
                if (account == null) actions.openLogin() else confirmSignOut()
            }
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account?.avatar != null) {
            Cover(account.avatar, Modifier.size(48.dp), circle = true, requestSize = RowCoverSize)
        } else {
            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceHigh), contentAlignment = Alignment.Center) {
                AmIcon(AmIcons.User, tint = colors.accent, size = 24.dp)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            AmText(account?.name ?: "Se connecter", style = AmTheme.type.headline, maxLines = 1)
            AmText(
                text = when {
                    account == null -> "Vos titres aimés, playlists et recommandations YouTube Music."
                    else -> account.email ?: "Compte YouTube Music"
                },
                style = AmTheme.type.footnote,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
        if (account == null) {
            AmIcon(AmIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
        } else {
            AmText("Déconnexion", style = AmTheme.type.subhead, color = colors.accent)
        }
    }
}

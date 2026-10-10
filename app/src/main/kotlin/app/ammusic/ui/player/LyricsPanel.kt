package app.ammusic.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.data.lyrics.LyricLine
import app.ammusic.data.lyrics.Lyrics
import app.ammusic.playback.PlayerState
import app.ammusic.ui.PlayerViewModel
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Soft = Color(0x99FFFFFF)
private val LineStyle = AmTheme.type.title.copy(fontSize = 27.sp, lineHeight = 34.sp)

/** Après un défilement à la main, les paroles attendent ce délai avant de se recaler sur la musique. */
private const val ResumeAfterMs = 3_000L

/** La ligne s'allume un peu avant d'être chantée : l'œil a le temps d'y arriver. */
private const val LeadMs = 180L

/**
 * Paroles : elles prennent la place des commandes, sur le fond animé du lecteur, pendant que la pochette
 * se range dans l'en-tête. La ligne chantée est nette et lumineuse, les autres s'estompent. Toucher une ligne y saute.
 */
@Composable
fun LyricsPanel(progress: () -> Float, top: Dp, state: PlayerState, player: PlayerViewModel, onClose: () -> Unit) {
    val visible by remember { derivedStateOf { progress() > 0f } }
    if (!visible) return
    val lyrics by player.lyrics.collectAsStateWithLifecycle()
    LaunchedEffect(state.track?.id) { player.loadLyrics() }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val shown = progress().coerceIn(0f, 1f)
                alpha = shown
                translationY = (1f - shown) * 40.dp.toPx()
            }
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(top = top)
            .navigationBarsPadding(),
    ) {
        // L'espace à gauche est celui où la pochette vient se ranger.
        Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 94.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AmText(state.track?.title.orEmpty(), style = AmTheme.type.headline, color = Color.White, maxLines = 1)
                AmText(state.track?.artists.orEmpty(), style = AmTheme.type.subhead, color = Soft, maxLines = 1)
            }
            IconButton(AmIcons.ChevronDown, label = "Fermer les paroles", onClick = onClose, tint = Color.White)
        }

        Box(Modifier.weight(1f).fillMaxWidth().fadedEdges()) {
            when (val current = lyrics) {
                null -> LyricsSkeleton()
                is Lyrics.Synced -> SyncedLyrics(current.lines, player)
                is Lyrics.Plain -> PlainLyrics(current.text)
                Lyrics.None -> Message("Pas de paroles trouvées pour ce titre.")
            }
        }

        Row(Modifier.fillMaxWidth().height(84.dp), Arrangement.Center, Alignment.CenterVertically) {
            IconButton(AmIcons.Previous, label = "Titre précédent", onClick = player::previous, tint = Color.White, iconSize = 30.dp)
            Box(
                Modifier.padding(horizontal = 28.dp).size(64.dp).pressable(scale = 0.86f, haptic = true, label = "Lecture ou pause", onClick = player::toggle),
                contentAlignment = Alignment.Center,
            ) {
                PlayPauseIcon(playing = state.isPlaying || state.isLoading, color = Color.White, size = 34.dp)
            }
            IconButton(AmIcons.Next, label = "Titre suivant", onClick = player::next, tint = if (state.hasNext) Color.White else Soft, iconSize = 30.dp)
        }
    }
}

/** Fond le haut et le bas de la zone dans le décor, pour que les lignes apparaissent et disparaissent en douceur. */
private fun Modifier.fadedEdges(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent),
        blendMode = BlendMode.DstIn,
    )
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, player: PlayerViewModel) {
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()

    // Position de lecture relue à chaque image ; seuls le numéro de ligne et le dessin des mots en dépendent.
    val position = remember { mutableLongStateOf(player.positionMs) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            position.longValue = player.positionMs
        }
    }
    val current by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.startMs <= position.longValue + LeadMs } }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = constraints.maxHeight
        val anchor = (viewport * 0.28f).toInt()
        val bottomSpace = with(LocalDensity.current) { (viewport * 0.6f).toDp() }

        val touchedAt = remember { mutableLongStateOf(0L) }
        LaunchedEffect(dragged) { if (dragged) touchedAt.longValue = System.currentTimeMillis() }
        LaunchedEffect(current, lines) {
            if (current < 0 || dragged) return@LaunchedEffect
            val wait = touchedAt.longValue + ResumeAfterMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            list.animateScrollToItem(current, scrollOffset = -anchor)
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = bottomSpace),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                LyricRow(
                    line = line,
                    distance = if (current < 0) 1 else abs(index - current),
                    position = { position.longValue },
                    onClick = {
                        touchedAt.longValue = 0L
                        player.seekTo(line.startMs)
                    },
                )
            }
        }
    }
}

/** [distance] : nombre de lignes entre celle-ci et la ligne chantée (0 = c'est elle). */
@Composable
private fun LyricRow(line: LyricLine, distance: Int, position: () -> Long, onClick: () -> Unit) {
    val active = distance == 0
    val soft = spring<Float>(dampingRatio = 0.9f, stiffness = 140f)
    val light by animateFloatAsState(if (active) 1f else 0.34f, soft, label = "light")
    val size by animateFloatAsState(if (active) 1f else 0.94f, soft, label = "size")
    val haze by animateDpAsState(if (active) 0.dp else (0.7f * distance.coerceAtMost(4)).dp, spring(stiffness = 140f), label = "haze")
    val text = line.text.ifBlank { "· · ·" }

    Box(
        Modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f, label = "Aller à ce passage", onClick = onClick)
            .padding(vertical = 10.dp)
            .graphicsLayer {
                alpha = light
                scaleX = size
                scaleY = size
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(haze, BlurredEdgeTreatment.Unbounded),
    ) {
        if (active && line.words.isNotEmpty()) {
            // Mot à mot : les mots déjà chantés sont pleins, celui en cours s'allume, les suivants attendent.
            val now = position()
            BasicText(
                text = buildAnnotatedString {
                    line.words.forEach { word ->
                        val sung = ((now - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.42f + 0.58f * sung))) { append(word.text) }
                    }
                },
                style = LineStyle,
            )
        } else {
            BasicText(text = text, style = LineStyle, color = { Color.White })
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 96.dp)) {
        AmText("Paroles non synchronisées", style = AmTheme.type.caption, color = Soft, modifier = Modifier.padding(bottom = 16.dp))
        AmText(text, style = AmTheme.type.section.copy(lineHeight = 32.sp), color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        AmText(text, style = AmTheme.type.headline, color = Soft)
    }
}

/** Lignes fantômes qui respirent pendant la recherche des paroles. */
@Composable
private fun LyricsSkeleton() {
    val pulse by rememberInfiniteTransition(label = "lyrics").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(Modifier.padding(start = 24.dp, top = 36.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf(0.78f, 0.55f, 0.84f, 0.42f, 0.7f, 0.6f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(24.dp).clip(RoundedCornerShape(8.dp)).drawBehind { drawRect(Color.White, alpha = pulse) })
        }
    }
}

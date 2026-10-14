package app.ammusic.ui.player

import app.ammusic.youtube.tr

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.data.UserSettings
import app.ammusic.data.lyrics.LyricLine
import app.ammusic.data.lyrics.Lyrics
import app.ammusic.playback.PlayerState
import app.ammusic.ui.PlayerViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private val Soft = Color(0x99FFFFFF)
private val LineStyles = listOf(
    AmTheme.type.title.copy(fontSize = 25.sp, lineHeight = 32.sp),
    AmTheme.type.title.copy(fontSize = 30.sp, lineHeight = 37.sp),
    AmTheme.type.title.copy(fontSize = 35.sp, lineHeight = 43.sp),
)

private const val ResumeAfterMs = 3_000L

private const val LeadMs = 180L

@Composable
fun LyricsPanel(progress: () -> Float, top: Dp, state: PlayerState, player: PlayerViewModel, onClose: () -> Unit) {
    val visible by remember { derivedStateOf { progress() > 0f } }
    if (!visible) return
    val lyrics by player.lyrics.collectAsStateWithLifecycle()
    val settings by UserSettings.state.collectAsStateWithLifecycle()
    val style = LineStyles[settings.lyricsSize.coerceIn(0, LineStyles.lastIndex)]
    val translation by player.translation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selecting by remember(state.track?.id) { mutableStateOf(false) }
    var selected by remember(state.track?.id) { mutableStateOf(emptySet<Int>()) }
    var sharing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val synced = lyrics as? Lyrics.Synced
    val stopSelecting = {
        selecting = false
        selected = emptySet()
    }
    LaunchedEffect(state.track?.id) { player.loadLyrics() }
    LaunchedEffect(synced, settings.translateLyrics) {
        if (synced != null && settings.translateLyrics) player.loadTranslation(synced.lines.map { it.text })
    }
    BackHandler(enabled = selecting, onBack = stopSelecting)

    Box(Modifier.fillMaxSize()) {
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
        Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 94.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AmText(state.track?.title.orEmpty(), style = AmTheme.type.headline, color = Color.White, maxLines = 1)
                AmText(state.track?.artists.orEmpty(), style = AmTheme.type.subhead, color = Soft, maxLines = 1)
            }
            if (synced != null && !selecting) {
                IconButton(
                    icon = AmIcons.Translate,
                    label = if (settings.translateLyrics) tr("Masquer la traduction", "Hide translation") else tr("Traduire", "Translate"),
                    onClick = { UserSettings.update { copy(translateLyrics = !translateLyrics) } },
                    tint = if (settings.translateLyrics) Color.White else Soft,
                )
                IconButton(AmIcons.Share, label = tr("Partager des paroles", "Share lyrics"), onClick = { selecting = true }, tint = Soft)
            }
            IconButton(AmIcons.ChevronDown, label = tr("Fermer les paroles", "Close lyrics"), onClick = onClose, tint = Color.White)
        }
        if (settings.translateLyrics && translation?.sameLanguage == true && !selecting) {
            AmText(
                text = tr("Ces paroles sont déjà en français.", "These lyrics are already in English."),
                style = AmTheme.type.footnote,
                color = Soft,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().fadedEdges()) {
            when (val current = lyrics) {
                null -> LyricsSkeleton()
                is Lyrics.Synced -> SyncedLyrics(
                    lines = current.lines,
                    player = player,
                    style = style,
                    wordByWord = settings.wordByWord,
                    translation = translation?.takeIf { settings.translateLyrics && !it.sameLanguage }?.lines,
                    selecting = selecting,
                    selected = selected,
                    onToggle = { index -> selected = if (index in selected) selected - index else if (selected.size < MaxShared) selected + index else selected },
                )
                is Lyrics.Plain -> PlainLyrics(current.text)
                Lyrics.None -> Message(tr("Pas de paroles trouvées pour ce titre.", "No lyrics found for this song."))
            }
        }

        if (selecting) {
            ShareBar(
                count = selected.size,
                busy = sharing,
                onCancel = stopSelecting,
                onShare = {
                    val track = state.track
                    val lines = synced?.lines
                    if (track != null && lines != null && selected.isNotEmpty() && !sharing) {
                        sharing = true
                        scope.launch {
                            preview = runCatching { renderLyricsCard(context, track, selected.sorted().map { lines[it].text }) }.getOrNull()
                            sharing = false
                        }
                    }
                },
            )
        } else {
        state.track?.let { track ->
            Box(Modifier.padding(start = 28.dp, end = 28.dp, top = 6.dp)) {
                ProgressSection(track.id, state.isPlaying, state.durationMs, player)
            }
        }
        Row(Modifier.fillMaxWidth().height(84.dp), Arrangement.Center, Alignment.CenterVertically) {
            SkipButton(forward = false, label = tr("Titre précédent", "Previous song"), enabled = true, size = 52.dp, iconSize = 30.dp) {
                SkipHint.mark(-1)
                player.previous()
            }
            Box(
                Modifier.padding(horizontal = 28.dp).size(64.dp).pressable(scale = 0.86f, haptic = true, label = tr("Lecture ou pause", "Play or pause"), onClick = player::toggle),
                contentAlignment = Alignment.Center,
            ) {
                PlayPauseIcon(playing = state.isPlaying || state.isLoading, color = Color.White, size = 34.dp)
            }
            SkipButton(forward = true, label = tr("Titre suivant", "Next song"), enabled = state.hasNext, size = 52.dp, iconSize = 30.dp) {
                SkipHint.mark(1)
                player.next()
            }
        }
        }
    }
    SharePreview(
        image = preview,
        onCancel = { preview = null },
        onShare = { image ->
            val track = state.track
            if (track != null) runCatching { shareCard(context, image, track) }
            preview = null
            stopSelecting()
        },
    )
    }
}

private fun Modifier.fadedEdges(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent),
        blendMode = BlendMode.DstIn,
    )
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    player: PlayerViewModel,
    style: TextStyle,
    wordByWord: Boolean,
    translation: List<String>?,
    selecting: Boolean,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()

    val position = remember { mutableLongStateOf(player.positionMs) }
    val waves = remember { mutableStateListOf<Wave>() }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                position.longValue = player.positionMs
                if (waves.isNotEmpty()) {
                    clock.longValue = now
                    waves.removeAll { now - it.startNanos > WaveLifeNanos }
                }
            }
        }
    }
    val shift = { index: Int ->
        var sum = 0f
        if (waves.isNotEmpty()) {
            val now = clock.longValue
            for (wave in waves) {
                val late = (index - wave.pivot).coerceIn(0, 9) * WaveStepNanos
                sum += wave.distance * settle((now - wave.startNanos - late) / 1e9f)
            }
        }
        sum
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
        LaunchedEffect(current, lines, selecting) {
            if (current < 0 || dragged || selecting) return@LaunchedEffect
            val wait = touchedAt.longValue + ResumeAfterMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            val now = withFrameNanos { it }
            val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
            if (row == null) {
                list.animateScrollToItem(current, scrollOffset = -anchor)
                return@LaunchedEffect
            }
            val moved = list.dispatchRawDelta((row.offset - anchor).toFloat())
            if (abs(moved) > 0.5f) {
                clock.longValue = now
                waves.add(Wave(now, moved, current))
            }
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 28.dp, bottom = bottomSpace),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                LyricRow(
                    line = line,
                    style = style,
                    wordByWord = wordByWord,
                    distance = if (current < 0) 1 else abs(index - current),
                    endMs = lines.getOrNull(index + 1)?.startMs ?: (line.startMs + 4_000L),
                    shift = { shift(index) },
                    position = { position.longValue },
                    translation = translation?.getOrNull(index),
                    selecting = selecting,
                    chosen = index in selected,
                    onClick = {
                        if (selecting) {
                            if (line.text.isNotBlank()) onToggle(index)
                        } else {
                            touchedAt.longValue = 0L
                            player.seekTo(line.startMs)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    style: TextStyle,
    wordByWord: Boolean,
    distance: Int,
    endMs: Long,
    shift: () -> Float,
    position: () -> Long,
    translation: String?,
    selecting: Boolean,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val playing = distance == 0
    val active = if (selecting) chosen else playing
    val soft = spring<Float>(dampingRatio = 0.82f, stiffness = 120f)
    val light by animateFloatAsState(if (active) 1f else 0.34f, soft, label = "light")
    val size by animateFloatAsState(if (active) 1f else 0.92f, soft, label = "size")
    val haze by animateDpAsState(if (active) 0.dp else if (selecting) 0.dp else (0.8f * distance.coerceAtMost(4)).dp, spring(stiffness = 120f), label = "haze")

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = shift() }
            .pressable(scale = 0.98f, label = tr("Aller à ce passage", "Jump to this line"), onClick = onClick)
            .padding(vertical = 1.dp)
            .graphicsLayer {
                alpha = light
                scaleX = size
                scaleY = size
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(haze, BlurredEdgeTreatment.Unbounded)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column {
        when {
            line.text.isBlank() -> InterludeDots(playing && !selecting) {
                ((position() - line.startMs).toFloat() / (endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            }

            playing && !selecting && wordByWord && line.words.isNotEmpty() -> {
                val now = position()
                BasicText(
                    text = buildAnnotatedString {
                        line.words.forEach { word ->
                            val sung = ((now - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                            if (sung <= 0f || sung >= 1f) {
                                withStyle(SpanStyle(color = Color.White.copy(alpha = if (sung >= 1f) 1f else Unsung))) { append(word.text) }
                            } else {
                                val head = sung * (word.text.length + 1)
                                word.text.forEachIndexed { index, letter ->
                                    val lit = (head - index).coerceIn(0f, 1f)
                                    withStyle(SpanStyle(color = Color.White.copy(alpha = Unsung + (1f - Unsung) * lit))) { append(letter) }
                                }
                            }
                        }
                    },
                    style = style,
                )
            }

            else -> BasicText(text = line.text, style = style, color = { Color.White })
        }
        if (!translation.isNullOrBlank() && line.text.isNotBlank()) {
            BasicText(
                text = translation,
                style = style.copy(fontSize = style.fontSize * 0.58f, lineHeight = style.lineHeight * 0.62f, fontWeight = FontWeight.Medium),
                color = { Color.White.copy(alpha = 0.62f) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        }
    }
}

@Composable
private fun InterludeDots(active: Boolean, progress: () -> Float) {
    val breath = if (active) {
        rememberInfiniteTransition(label = "dots").animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "breath")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val awake by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 1f, stiffness = 200f), label = "awake")
    Canvas(Modifier.height(37.dp).fillMaxWidth()) {
        val radius = 5.5.dp.toPx()
        val gap = 8.dp.toPx()
        val filled = progress()
        val swell = 1f + 0.12f * awake * sin(breath.value * 2f * PI.toFloat())
        repeat(3) { index ->
            val lit = if (active) (filled * 3f - index).coerceIn(0f, 1f) else 1f
            val center = Offset((radius + index * (2f * radius + gap)) * swell, size.height / 2f)
            drawCircle(Color.White, radius * swell, center, alpha = if (active) Unsung + (1f - Unsung) * lit else 1f)
        }
    }
}

private class Wave(val startNanos: Long, val distance: Float, val pivot: Int)

private const val WaveStepNanos = 34_000_000L
private const val WaveLifeNanos = 1_600_000_000L
private const val Unsung = 0.42f

private fun settle(seconds: Float): Float {
    if (seconds <= 0f) return 1f
    val pull = 11.5f
    val damping = 0.86f
    val swing = pull * sqrt(1f - damping * damping)
    return exp(-damping * pull * seconds) * (cos(swing * seconds) + damping * pull / swing * sin(swing * seconds))
}

@Composable
private fun PlainLyrics(text: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 96.dp)) {
        AmText(tr("Paroles non synchronisées", "Unsynced lyrics"), style = AmTheme.type.caption, color = Soft, modifier = Modifier.padding(bottom = 16.dp))
        AmText(text, style = AmTheme.type.section.copy(lineHeight = 32.sp), color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        AmText(text, style = AmTheme.type.headline, color = Soft)
    }
}

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

private const val MaxShared = 6

@Composable
private fun ShareBar(count: Int, busy: Boolean, onCancel: () -> Unit, onShare: () -> Unit) {
    val ready = count > 0 && !busy
    val shown by animateFloatAsState(if (ready) 1f else 0.4f, spring(dampingRatio = 1f, stiffness = 420f), label = "share")
    Row(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        AmText(
            text = tr("Annuler", "Cancel"),
            style = AmTheme.type.headline,
            color = Color.White,
            modifier = Modifier.pressable(label = tr("Annuler", "Cancel"), onClick = onCancel).padding(horizontal = 8.dp, vertical = 14.dp),
        )
        AmText(
            text = if (count == 0) tr("Touchez des lignes", "Tap some lines") else tr("$count sur $MaxShared", "$count of $MaxShared"),
            style = AmTheme.type.subhead,
            color = Soft,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .height(44.dp)
                .graphicsLayer { alpha = shown }
                .pressable(haptic = true, label = tr("Partager", "Share")) { if (ready) onShare() }
                .clip(CircleShape)
                .background(Color.White)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmIcon(AmIcons.Share, tint = Color(0xFF141216), size = 18.dp)
            AmText(tr("Partager", "Share"), style = AmTheme.type.headline, color = Color(0xFF141216), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SharePreview(image: Bitmap?, onCancel: () -> Unit, onShare: (Bitmap) -> Unit) {
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(image) {
        if (image != null) {
            shown = image
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            shown = null
        }
    }
    val current = shown ?: return
    BackHandler(enabled = image != null, onBack = onCancel)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = 0.72f * progress.value.coerceIn(0f, 1f)) }
            .pointerInput(Unit) { detectTapGestures { onCancel() } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = tr("Aperçu de la carte", "Card preview"),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 110.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.86f + 0.14f * p
                    scaleY = scaleX
                    translationY = (1f - p) * 40.dp.toPx()
                }
                .shadow(30.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmText(
                text = tr("Annuler", "Cancel"),
                style = AmTheme.type.headline,
                color = Color.White,
                modifier = Modifier.pressable(label = tr("Annuler", "Cancel"), onClick = onCancel).padding(horizontal = 8.dp, vertical = 14.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .height(48.dp)
                    .pressable(haptic = true, label = tr("Partager", "Share")) { onShare(current) }
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmIcon(AmIcons.Share, tint = Color(0xFF141216), size = 18.dp)
                AmText(tr("Partager", "Share"), style = AmTheme.type.headline, color = Color(0xFF141216), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

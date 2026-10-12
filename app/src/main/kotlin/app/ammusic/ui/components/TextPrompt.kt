package app.ammusic.ui.components

import app.ammusic.youtube.tr

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme

class PromptRequest(val title: String, val initial: String, val thumbnail: String?, val onConfirm: (String) -> Unit)

@Composable
fun TextPrompt(request: PromptRequest?, onDismiss: () -> Unit) {
    val colors = AmTheme.colors
    var shown by remember { mutableStateOf<PromptRequest?>(null) }
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }

    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            pop.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            shown = null
        }
    }
    LaunchedEffect(request) {
        if (request != null) pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f))
    }

    val current = shown ?: return
    var text by remember(current) { mutableStateOf(TextFieldValue(current.initial, TextRange(0, current.initial.length))) }
    val focus = remember { FocusRequester() }
    val valid = text.text.isNotBlank()
    val confirmLabel = if (current.initial.isEmpty()) tr("Créer", "Create") else "OK"
    val confirm = {
        if (valid) {
            current.onConfirm(text.text.trim())
            onDismiss()
        }
    }
    LaunchedEffect(current) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = request != null, onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = 0.52f * progress.value.coerceIn(0f, 1f)) }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                .pointerInput(Unit) { detectTapGestures {} }
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(top = 8.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.textTertiary.copy(alpha = 0.5f)))
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
                AmText(
                    text = tr("Annuler", "Cancel"),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .pressable(label = tr("Annuler", "Cancel"), onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                )
                AmText(current.title, style = AmTheme.type.headline, maxLines = 1, modifier = Modifier.align(Alignment.Center))
                AmText(
                    text = confirmLabel,
                    style = AmTheme.type.headline,
                    color = if (valid) colors.accent else colors.textTertiary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .pressable(haptic = true, label = confirmLabel, onClick = confirm)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
            Cover(
                url = current.thumbnail,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(132.dp)
                    .graphicsLayer {
                        scaleX = 0.72f + 0.28f * pop.value
                        scaleY = scaleX
                        alpha = pop.value.coerceIn(0f, 1f)
                    }
                    .background(colors.surfaceHigh, RoundedCornerShape(12.dp)),
                corner = 12.dp,
                requestSize = TileCoverSize,
                elevated = true,
                placeholder = false,
                glyph = AmIcons.Playlist,
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = AmTheme.type.section.copy(color = colors.text, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp).fillMaxWidth().focusRequester(focus),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.text.isEmpty()) {
                            AmText(
                                text = tr("Titre de la playlist", "Playlist title"),
                                style = AmTheme.type.section,
                                color = colors.textTertiary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                        field()
                    }
                },
            )
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 26.dp)
                    .width(196.dp)
                    .height(1.dp)
                    .background(if (valid) colors.accent.copy(alpha = 0.7f) else colors.hairline),
            )
        }
    }
}

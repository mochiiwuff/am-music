package app.ammusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme

/** Demande d'un texte court : [title] de la boîte, valeur de départ, et quoi faire du texte validé. */
class PromptRequest(val title: String, val initial: String, val onConfirm: (String) -> Unit)

/** Boîte de saisie au centre de l'écran (nom d'une playlist). [request] à `null` la referme. */
@Composable
fun TextPrompt(request: PromptRequest?, onDismiss: () -> Unit) {
    val colors = AmTheme.colors
    var shown by remember { mutableStateOf<PromptRequest?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 640f))
            shown = null
        }
    }

    val current = shown ?: return
    var text by remember(current) { mutableStateOf(TextFieldValue(current.initial, TextRange(0, current.initial.length))) }
    val focus = remember { FocusRequester() }
    val valid = text.text.isNotBlank()
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 36.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress.value.coerceIn(0f, 1f)
                    scaleX = 0.92f + 0.08f * progress.value
                    scaleY = scaleX
                }
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        ) {
            AmText(current.title, style = AmTheme.type.headline)
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHigh)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = AmTheme.type.body.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AmText(
                    text = "Annuler",
                    style = AmTheme.type.headline,
                    color = colors.textSecondary,
                    modifier = Modifier.pressable(label = "Annuler", onClick = onDismiss).padding(horizontal = 14.dp, vertical = 14.dp),
                )
                AmText(
                    text = "OK",
                    style = AmTheme.type.headline,
                    color = if (valid) colors.accent else colors.textTertiary,
                    modifier = Modifier.pressable(haptic = true, label = "Valider", onClick = confirm).padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
        }
    }
}

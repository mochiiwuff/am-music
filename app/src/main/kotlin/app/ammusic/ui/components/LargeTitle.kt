package app.ammusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme
import dev.chrisbanes.haze.hazeSource

private val TopBarHeight = 48.dp

/**
 * Écran à grand titre : le titre défile avec le contenu, puis réapparaît en petit
 * dans la barre du haut, qui devient un verre dépoli.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = AmTheme.colors
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val collapse = remember(state, threshold) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / threshold).coerceIn(0f, 1f)
        }
    }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current).background(colors.background),
            contentPadding = PaddingValues(top = statusBar + TopBarHeight, bottom = LocalBottomInset.current),
        ) {
            item(key = "largeTitle", contentType = "largeTitle") {
                AmText(
                    text = title,
                    style = AmTheme.type.largeTitle,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
                        .graphicsLayer { alpha = 1f - collapse.value },
                )
            }
            content()
        }

        Box(Modifier.fillMaxWidth().height(statusBar + TopBarHeight)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = collapse.value }
                    .glass(RectangleShape, bordered = false)
                    .drawBehind {
                        drawLine(colors.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
            )
            Box(Modifier.fillMaxSize().padding(top = statusBar)) {
                if (leading != null) Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { leading() }
                AmText(
                    text = title,
                    style = AmTheme.type.headline,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer {
                        alpha = collapse.value
                        translationY = (1f - collapse.value) * 8.dp.toPx()
                    },
                )
                if (trailing != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { trailing() }
            }
        }
    }
}

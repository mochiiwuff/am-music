package app.ammusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.SearchResults
import app.ammusic.ui.SearchViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.ErrorState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.shimmer
import app.ammusic.ui.home.MoodRow
import app.ammusic.ui.home.ProfileButton
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack

@Composable
fun SearchScreen(onProfile: () -> Unit, onPlay: (YtTrack) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val submit: (String) -> Unit = {
        focus.clearFocus()
        viewModel.submit(it)
    }

    LargeTitleScreen(title = "Recherche", trailing = { ProfileButton(onProfile) }) {
        item(key = "field", contentType = "field") {
            SearchField(state.query, onChange = viewModel::onQuery, onSubmit = { submit(state.query) })
        }

        val results = state.results
        when {
            state.query.isBlank() -> {
                if (state.moods.isNotEmpty()) {
                    item(key = "browseHeader", contentType = "header") { SectionHeader("Parcourir les catégories") }
                    items(state.moods.chunked(2), key = { it.first().title }, contentType = { "tiles" }) { pair ->
                        MoodRow(pair, height = 96)
                    }
                }
            }

            results is SearchResults.None -> items(state.suggestions, key = { it }, contentType = { "suggestion" }) { suggestion ->
                Row(
                    Modifier.fillMaxWidth().pressable(scale = 0.98f) { submit(suggestion) }.padding(horizontal = 20.dp).height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AmIcon(AmIcons.Search, tint = AmTheme.colors.textTertiary, size = 18.dp)
                    AmText(suggestion, maxLines = 1, modifier = Modifier.padding(start = 14.dp))
                }
            }

            results is SearchResults.Loading -> item(key = "loading") { ResultsSkeleton() }

            results is SearchResults.Error -> item(key = "error") {
                ErrorState(
                    title = "Recherche impossible",
                    message = "Vérifiez votre connexion internet, puis réessayez.",
                    onRetry = { viewModel.submit() },
                )
            }

            results is SearchResults.Content -> {
                if (results.sections.isEmpty()) {
                    item(key = "empty") {
                        AmText(
                            text = "Aucun résultat pour « ${state.query} ».",
                            color = AmTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                        )
                    }
                }
                results.sections.forEachIndexed { index, section ->
                    item(key = "header$index", contentType = "header") { SectionHeader(section.title, top = 24.dp) }
                    items(section.items, key = { "$index:${it.key}" }, contentType = { "result" }) { item ->
                        ItemRow(
                            item = item,
                            onClick = { if (item is YtTrack) onPlay(item) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val colors = AmTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(AmIcons.Search, tint = colors.textSecondary, size = 20.dp)
        Box(Modifier.padding(start = 10.dp).weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                AmText("Artistes, titres, albums…", color = colors.textTertiary, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = AmTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(AmIcons.Close, label = "Effacer", onClick = { onChange("") }, tint = colors.textSecondary, iconSize = 18.dp)
        }
    }
}

@Composable
private fun ResultsSkeleton() {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(7) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).shimmer(RoundedCornerShape(8.dp)))
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).shimmer(RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(0.35f).height(12.dp).shimmer(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

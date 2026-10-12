package app.ammusic.ui.search

import app.ammusic.youtube.tr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
import app.ammusic.ui.components.listRow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.home.MoodRow
import app.ammusic.ui.home.ProfileButton
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtSearchFilter

@Composable
fun SearchScreen(onProfile: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val submit: (String) -> Unit = {
        focus.clearFocus()
        viewModel.submit(it)
    }

    LargeTitleScreen(title = tr("Recherche", "Search"), trailing = { ProfileButton(onProfile) }) {
        item(key = "field", contentType = "field") {
            SearchField(state.query, onChange = viewModel::onQuery, onSubmit = { submit(state.query) })
        }

        val results = state.results
        if (state.query.isNotBlank() && results !is SearchResults.None) {
            item(key = "filters", contentType = "filters") { FilterChips(state.filter, viewModel::onFilter) }
        }
        when {
            state.query.isBlank() -> {
                if (state.history.isNotEmpty()) {
                    item(key = "historyHeader", contentType = "header") {
                        Row(verticalAlignment = Alignment.Bottom) {
                            SectionHeader(tr("Recherches récentes", "Recent searches"), Modifier.weight(1f), top = 20.dp)
                            AmText(
                                text = tr("Effacer", "Clear"),
                                style = AmTheme.type.subhead,
                                color = AmTheme.colors.accent,
                                modifier = Modifier
                                    .pressable(label = tr("Effacer l'historique", "Clear history"), onClick = viewModel::clearHistory)
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }
                    items(state.history, key = { "history:$it" }, contentType = { "history" }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().animateItem().pressable(scale = 0.98f) { submit(entry) }.padding(start = 20.dp, end = 8.dp).height(50.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AmIcon(AmIcons.Clock, tint = AmTheme.colors.textTertiary, size = 18.dp)
                            AmText(entry, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 14.dp))
                            IconButton(
                                icon = AmIcons.Close,
                                label = tr("Retirer de l'historique", "Remove from history"),
                                onClick = { viewModel.forget(entry) },
                                tint = AmTheme.colors.textTertiary,
                                iconSize = 16.dp,
                            )
                        }
                    }
                }
                if (state.moods.isNotEmpty()) {
                    item(key = "browseHeader", contentType = "header") { SectionHeader(tr("Parcourir les catégories", "Browse categories")) }
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

            results is SearchResults.Loading -> item(key = "loading") { RowsSkeleton(7, Modifier.padding(vertical = 20.dp)) }

            results is SearchResults.Error -> item(key = "error") {
                ErrorState(
                    title = tr("Recherche impossible", "Search failed"),
                    message = tr("Vérifiez votre connexion internet, puis réessayez.", "Check your internet connection, then try again."),
                    onRetry = { viewModel.submit() },
                )
            }

            results is SearchResults.Content -> {
                if (results.sections.isEmpty()) {
                    item(key = "empty") {
                        AmText(
                            text = tr("Aucun résultat pour « ${state.query} ».", "No results for “${state.query}”."),
                            color = AmTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                        )
                    }
                }
                results.sections.forEachIndexed { index, section ->
                    val filter = SectionFilters[section.title]
                    item(key = "header$index", contentType = "header") {
                        SectionHeader(section.title, top = if (index == 0) 12.dp else 24.dp, onMore = filter?.let { { viewModel.onFilter(it) } })
                    }
                    items(section.items, key = { "$index:${it.key}" }, contentType = { "result" }) { item ->
                        ItemRow(item, "search$index", Modifier.fillMaxWidth().listRow())
                    }
                }
            }

            results is SearchResults.Filtered -> {
                if (results.items.isEmpty()) {
                    item(key = "empty") {
                        AmText(
                            text = tr("Aucun résultat de ce type pour « ${state.query} ».", "No results of this kind for “${state.query}”."),
                            color = AmTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                        )
                    }
                }
                items(results.items, key = { it.key }, contentType = { "result" }) { item ->
                    ItemRow(item, "searchFiltered", Modifier.fillMaxWidth().listRow())
                }
                if (results.continuation != null) {
                    item(key = "more", contentType = "more") {
                        LaunchedEffect(results.continuation) { viewModel.loadMore() }
                        RowsSkeleton(2, Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

private val SectionFilters get() = mapOf(
    tr("Titres", "Songs") to YtSearchFilter.Songs,
    tr("Clips", "Videos") to YtSearchFilter.Videos,
    "Albums" to YtSearchFilter.Albums,
    tr("Artistes", "Artists") to YtSearchFilter.Artists,
    tr("Playlists de la communauté", "Community playlists") to YtSearchFilter.Playlists,
)

private val Filters get() = listOf(
    null to tr("Tout", "All"),
    YtSearchFilter.Songs to tr("Titres", "Songs"),
    YtSearchFilter.Albums to "Albums",
    YtSearchFilter.Artists to tr("Artistes", "Artists"),
    YtSearchFilter.Playlists to "Playlists",
    YtSearchFilter.Videos to tr("Clips", "Videos"),
)

@Composable
private fun FilterChips(selected: YtSearchFilter?, onSelect: (YtSearchFilter?) -> Unit) {
    val colors = AmTheme.colors
    val soft = spring<Color>(stiffness = 420f)
    LazyRow(
        modifier = Modifier.padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Filters, key = { it.second }) { (filter, label) ->
            val active = filter == selected
            val fill by animateColorAsState(if (active) colors.accent else colors.surface, soft, label = "chip")
            val text by animateColorAsState(if (active) Color.White else colors.text, soft, label = "chipText")
            Box(Modifier.height(48.dp).pressable(haptic = true, label = label) { onSelect(filter) }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier.height(36.dp).clip(CircleShape).drawBehind { drawRect(fill) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AmText(label, style = AmTheme.type.subhead, color = text)
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
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(AmIcons.Search, tint = colors.textSecondary, size = 20.dp)
        Box(Modifier.padding(start = 10.dp).weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                AmText(tr("Artistes, titres, albums…", "Artists, songs, albums…"), color = colors.textTertiary, maxLines = 1)
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
            IconButton(AmIcons.Close, label = tr("Effacer", "Clear"), onClick = { onChange("") }, tint = colors.textSecondary, iconSize = 18.dp)
        }
    }
}


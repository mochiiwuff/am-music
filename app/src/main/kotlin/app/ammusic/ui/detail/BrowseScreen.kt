package app.ammusic.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.BrowseViewModel
import app.ammusic.ui.LoadStatus
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.ErrorState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemCarousel
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.ItemTile
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.TileCoverSize
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack

/**
 * Page faite de sections : une humeur, un genre, ou la liste complète d'une section d'artiste.
 * Une section seule s'étale en liste (titres) ou en grille sur 2 colonnes ; plusieurs deviennent des carrousels.
 */
@Composable
fun BrowseScreen(viewModel: BrowseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val sections = state.page?.sections.orEmpty()

    LargeTitleScreen(
        title = state.title,
        leading = { IconButton(AmIcons.ChevronLeft, label = "Retour", onClick = actions.back) },
    ) {
        when {
            state.status == LoadStatus.Loading -> item(key = "skeleton") { RowsSkeleton(8, Modifier.padding(top = 16.dp)) }

            state.status == LoadStatus.Error -> item(key = "error") {
                ErrorState(
                    title = "Contenu indisponible",
                    message = "Vérifiez votre connexion internet, puis réessayez.",
                    onRetry = viewModel::load,
                )
            }

            sections.isEmpty() -> item(key = "empty") {
                AmText(
                    text = "Rien à afficher ici pour le moment.",
                    color = AmTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                )
            }

            sections.size == 1 -> {
                val items = sections.first().items
                if (items.all { it is YtTrack }) {
                    val tracks = items.filterIsInstance<YtTrack>()
                    itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                        ItemRow(
                            item = track,
                            origin = "browse",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            onClick = { actions.playAll(tracks, index, null) },
                        )
                    }
                } else {
                    items(items.chunked(2), key = { it.first().key }, contentType = { "tiles" }) { pair ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            pair.forEach { item ->
                                ItemTile(item, "browse", Modifier.weight(1f)) { actions.open(item, "browse", TileCoverSize) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            else -> sections.forEachIndexed { index, section ->
                item(key = "header$index", contentType = "header") {
                    SectionHeader(section.title, top = if (index == 0) 8.dp else 32.dp)
                }
                item(key = "section$index", contentType = "carousel") { ItemCarousel(section.items, "browse$index") }
            }
        }
    }
}

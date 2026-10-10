package app.ammusic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.ammusic.data.fake.FakeData
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Artwork
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.pressable
import app.ammusic.ui.home.ProfileButton
import app.ammusic.ui.theme.AmTheme
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleArrowDown
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Disc3
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.ListMusic
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MicVocal

private data class LibraryEntry(val label: String, val icon: ImageVector)

private val Entries = listOf(
    LibraryEntry("Titres aimés", Lucide.Heart),
    LibraryEntry("Playlists", Lucide.ListMusic),
    LibraryEntry("Albums", Lucide.Disc3),
    LibraryEntry("Artistes", Lucide.MicVocal),
    LibraryEntry("Téléchargements", Lucide.CircleArrowDown),
    LibraryEntry("Historique", Lucide.Clock),
)

@Composable
fun LibraryScreen(onProfile: () -> Unit) {
    LargeTitleScreen(title = "Bibliothèque", trailing = { ProfileButton(onProfile) }) {
        items(Entries, key = { it.label }, contentType = { "entry" }) { entry -> EntryRow(entry) }

        item(key = "recentHeader", contentType = "header") { SectionHeader("Ajouts récents") }
        items(FakeData.libraryAlbums.chunked(2), key = { it.first().id }, contentType = { "albums" }) { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                pair.forEach { album ->
                    Column(Modifier.weight(1f).pressable {}) {
                        Artwork(album.artSeed, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp, elevated = true)
                        Spacer(Modifier.height(8.dp))
                        AmText(album.title, style = AmTheme.type.subhead, maxLines = 1)
                        AmText(album.subtitle, style = AmTheme.type.footnote, color = AmTheme.colors.textSecondary, maxLines = 1)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LibraryEntry) {
    val colors = AmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f) {}
            .padding(start = 20.dp)
            .height(56.dp)
            .drawBehind {
                val x = 38.dp.toPx()
                drawLine(colors.hairline, Offset(x, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(entry.icon, tint = colors.accent, size = 24.dp)
        AmText(entry.label, style = AmTheme.type.body.copy(fontSize = AmTheme.type.section.fontSize.times(0.86f)), modifier = Modifier.weight(1f).padding(start = 14.dp), maxLines = 1)
        AmIcon(Lucide.ChevronRight, tint = colors.textTertiary, size = 18.dp)
    }
}

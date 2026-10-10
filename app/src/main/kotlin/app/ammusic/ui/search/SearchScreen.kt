package app.ammusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.ammusic.data.fake.FakeData
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.home.ProfileButton
import app.ammusic.ui.home.TileRow
import app.ammusic.ui.theme.AmTheme

@Composable
fun SearchScreen(onProfile: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    LargeTitleScreen(title = "Recherche", trailing = { ProfileButton(onProfile) }) {
        item(key = "field", contentType = "field") { SearchField(query, onChange = { query = it }) }
        item(key = "browseHeader", contentType = "header") { SectionHeader("Parcourir les catégories") }
        items(FakeData.categories.chunked(2), key = { it.first().id }, contentType = { "tiles" }) { pair ->
            TileRow(pair, height = 104)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = AmTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp),
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

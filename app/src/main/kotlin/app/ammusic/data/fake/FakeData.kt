package app.ammusic.data.fake

import app.ammusic.domain.model.MediaCard

/** Fausses données restantes : la bibliothèque, remplacée par la vraie en phase 5. */
object FakeData {
    val libraryAlbums = listOf(
        MediaCard(10, "Les Heures bleues", "Clara Vasseur", artSeed = 3),
        MediaCard(11, "Marée haute", "Oslo Novembre", artSeed = 12),
        MediaCard(12, "Néons", "Juno Lacroix", artSeed = 8),
        MediaCard(13, "Petite Ceinture", "Les Passagers", artSeed = 15),
        MediaCard(40, "Solstice", "Nova Blanche", artSeed = 9),
        MediaCard(41, "Les Grands Boulevards", "Théo Marchand", artSeed = 30),
    )
}

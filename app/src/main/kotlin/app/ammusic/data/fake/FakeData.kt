package app.ammusic.data.fake

import app.ammusic.domain.model.MediaCard

/** Fausses données de la phase 1, remplacées par YouTube Music en phase 2. */
object FakeData {
    val nowPlaying = MediaCard(900, "Minuit à Belleville", "Clara Vasseur", artSeed = 3)

    val mixes = listOf(
        MediaCard(1, "Nuit blanche", "Électro douce, voix feutrées", artSeed = 7),
        MediaCard(2, "Dimanche matin", "Folk, piano, café", artSeed = 2),
        MediaCard(3, "Plein soleil", "Pop française et funk", artSeed = 1),
        MediaCard(4, "Concentration", "Instrumentaux calmes", artSeed = 5),
    )

    val recent = listOf(
        MediaCard(10, "Les Heures bleues", "Clara Vasseur", artSeed = 3),
        MediaCard(11, "Marée haute", "Oslo Novembre", artSeed = 12),
        MediaCard(12, "Néons", "Juno Lacroix", artSeed = 8),
        MediaCard(13, "Petite Ceinture", "Les Passagers", artSeed = 15),
        MediaCard(14, "Atlas", "Mira Delorme", artSeed = 21),
    )

    val recommended = listOf(
        MediaCard(20, "Minuit à Belleville", "Clara Vasseur", artSeed = 3),
        MediaCard(21, "Ciel de traîne", "Oslo Novembre", artSeed = 12),
        MediaCard(22, "Vertige", "Juno Lacroix", artSeed = 8),
        MediaCard(23, "Rue des Martyrs", "Les Passagers", artSeed = 15),
        MediaCard(24, "Sable", "Mira Delorme", artSeed = 21),
        MediaCard(25, "Dernier métro", "Théo Marchand", artSeed = 30),
        MediaCard(26, "Lagune", "Anouk & Sol", artSeed = 26),
        MediaCard(27, "Fièvre", "Nova Blanche", artSeed = 9),
        MediaCard(28, "Contre-jour", "Élise Faure", artSeed = 17),
    )

    val newReleases = listOf(
        MediaCard(40, "Solstice", "Nova Blanche", artSeed = 9),
        MediaCard(41, "Les Grands Boulevards", "Théo Marchand", artSeed = 30),
        MediaCard(42, "Eau vive", "Anouk & Sol", artSeed = 26),
        MediaCard(43, "Chambre 12", "Élise Faure", artSeed = 17),
    )

    val moods = listOf(
        MediaCard(60, "Détente", "", artSeed = 4),
        MediaCard(61, "Énergie", "", artSeed = 1),
        MediaCard(62, "Fête", "", artSeed = 7),
        MediaCard(63, "Romance", "", artSeed = 16),
        MediaCard(64, "Sport", "", artSeed = 11),
        MediaCard(65, "Sommeil", "", artSeed = 5),
    )

    val categories = listOf(
        MediaCard(70, "Pop", "", artSeed = 16),
        MediaCard(71, "Rap français", "", artSeed = 13),
        MediaCard(72, "Électro", "", artSeed = 6),
        MediaCard(73, "Rock", "", artSeed = 23),
        MediaCard(74, "Jazz", "", artSeed = 18),
        MediaCard(75, "Classique", "", artSeed = 27),
        MediaCard(76, "R&B et soul", "", artSeed = 14),
        MediaCard(77, "Chanson française", "", artSeed = 2),
    )

    val libraryAlbums = recent + newReleases
}

package app.ammusic.domain.model

/** Élément affichable (titre, album, playlist, mix…). [artSeed] sert à la pochette factice de la phase 1. */
data class MediaCard(
    val id: Int,
    val title: String,
    val subtitle: String,
    val artSeed: Int = id,
)

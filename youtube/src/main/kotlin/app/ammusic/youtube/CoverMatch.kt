package app.ammusic.youtube

import java.text.Normalizer

internal data class SongGuess(val title: String, val artist: String)

private val Bracketed = Regex("""\s*[(\[][^)\]]*[)\]]""")
private val Extras = Regex("""offici|video|vidéo|clip|audio|lyric|parole|visuali|\bhd\b|\bhq\b|\b4k\b|\bmv\b|m/v|\bfeat\b|\bft\b""", RegexOption.IGNORE_CASE)
private val Featuring = Regex("""\s+(feat|ft)\.?\s.*$""", RegexOption.IGNORE_CASE)
private val Marks = Regex("""\p{M}+""")
private val Separators = Regex("""[^\p{L}\p{N}]+""")

internal fun plainKey(text: String): String =
    Marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().replace(Separators, " ").trim()

internal fun songOnly(title: String): String {
    val kept = Bracketed.replace(title) { match -> if (Extras.containsMatchIn(match.value)) "" else match.value }
    return Featuring.replace(kept, "").trim().ifBlank { title }
}

internal fun songGuesses(videoTitle: String, artists: String): List<SongGuess> {
    val title = songOnly(videoTitle)
    val artist = artists.substringBefore(",").trim()
    val dash = title.indexOf(" - ")
    if (dash <= 0) return listOf(SongGuess(title, artist))
    val left = title.substring(0, dash).trim()
    val right = title.substring(dash + 3).trim()
    return listOf(SongGuess(right, left), SongGuess(title, artist))
}

internal fun sameSong(candidateTitle: String, candidateArtists: String, guess: SongGuess): Boolean {
    val title = plainKey(guess.title)
    if (title.isEmpty() || title != plainKey(songOnly(candidateTitle))) return false
    val artist = plainKey(guess.artist)
    val names = plainKey(candidateArtists)
    return artist.isNotEmpty() && names.isNotEmpty() && (names.contains(artist) || artist.contains(names))
}

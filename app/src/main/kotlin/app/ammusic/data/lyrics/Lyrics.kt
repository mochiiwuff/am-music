package app.ammusic.data.lyrics

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Un mot et le moment où il est chanté (paroles « mot à mot »). */
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

/** Une ligne de paroles. [words] est vide quand la source ne donne que le début de la ligne. */
data class LyricLine(val startMs: Long, val text: String, val words: List<LyricWord> = emptyList())

sealed interface Lyrics {
    /** Paroles calées sur la musique, ligne par ligne (et parfois mot par mot). */
    data class Synced(val lines: List<LyricLine>) : Lyrics

    /** Texte seul, sans repères de temps. */
    data class Plain(val text: String) : Lyrics

    data object None : Lyrics
}

private val LrcStamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

/** Lit le format LRC : `[mm:ss.cc] texte`, une ligne pouvant porter plusieurs repères. */
fun parseLrc(lrc: String): List<LyricLine> = lrc.lineSequence().flatMap { raw ->
    val stamps = LrcStamp.findAll(raw).toList()
    val text = raw.replace(LrcStamp, "").trim()
    stamps.asSequence().map { stamp ->
        val (minutes, seconds, fraction) = stamp.destructured
        val millis = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').toLong()
        LyricLine(minutes.toLong() * 60_000 + seconds.toLong() * 1000 + millis, text)
    }
}.sortedBy { it.startMs }.toList()

/** « 1:02.345 », « 00:01:02.345 », « 62.345 » ou « 62.345s » → millisecondes. */
private fun parseClock(value: String): Long? {
    val parts = value.trim().removeSuffix("s").split(':')
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
    val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
    return hours * 3_600_000 + minutes * 60_000 + (seconds * 1000).toLong()
}

/**
 * Lit des paroles TTML : chaque `<p>` est une ligne, chaque `<span>` daté un mot.
 * Les chœurs (spans imbriqués) sont rangés dans la ligne qui les contient.
 */
fun parseTtml(ttml: String): List<LyricLine> = runCatching {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(ttml))
    }
    val lines = ArrayList<LyricLine>()
    var lineStart: Long? = null
    val words = ArrayList<LyricWord>()
    val text = StringBuilder()
    // Pour chaque span ouvert : ses repères de temps, ou `null` s'il n'en a pas.
    val spans = ArrayDeque<Pair<Long, Long>?>()

    fun attr(name: String): String? = (0 until parser.attributeCount).firstOrNull { parser.getAttributeName(it) == name }?.let(parser::getAttributeValue)

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "p" -> {
                    lineStart = attr("begin")?.let(::parseClock)
                    words.clear()
                    text.clear()
                    spans.clear()
                }
                "span" -> {
                    val begin = attr("begin")?.let(::parseClock)
                    val end = attr("end")?.let(::parseClock)
                    spans.addLast(if (begin != null && end != null) begin to end else null)
                }
            }
            XmlPullParser.TEXT -> {
                val chunk = parser.text.orEmpty()
                val timing = spans.lastOrNull()
                if (chunk.isBlank()) {
                    if (text.isNotEmpty() && !text.endsWith(' ')) text.append(' ')
                } else {
                    if (timing != null) words += LyricWord(chunk.trim(), timing.first, timing.second)
                    text.append(chunk.trimStart())
                    if (chunk.last().isWhitespace() && !text.endsWith(' ')) text.append(' ')
                }
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "span" -> spans.removeLastOrNull()
                "p" -> {
                    val start = lineStart ?: words.firstOrNull()?.startMs
                    if (start != null && text.isNotBlank()) lines += LyricLine(start, text.toString().trim(), words.toList())
                    lineStart = null
                }
            }
        }
    }
    lines.sortedBy { it.startMs }
}.onFailure { Log.w("AmLyrics", "TTML illisible : $it") }.getOrDefault(emptyList())

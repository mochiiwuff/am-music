package app.ammusic.data.lyrics

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

data class LyricLine(val startMs: Long, val text: String, val words: List<LyricWord> = emptyList())

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics

    data class Plain(val text: String) : Lyrics

    data object None : Lyrics
}

private val LrcStamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

fun parseLrc(lrc: String): List<LyricLine> = lrc.lineSequence().flatMap { raw ->
    val stamps = LrcStamp.findAll(raw).toList()
    val text = raw.replace(LrcStamp, "").trim()
    stamps.asSequence().map { stamp ->
        val (minutes, seconds, fraction) = stamp.destructured
        val millis = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').toLong()
        LyricLine(minutes.toLong() * 60_000 + seconds.toLong() * 1000 + millis, text)
    }
}.sortedBy { it.startMs }.toList()

private fun parseClock(value: String): Long? {
    val parts = value.trim().removeSuffix("s").split(':')
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
    val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
    return hours * 3_600_000 + minutes * 60_000 + (seconds * 1000).toLong()
}

fun parseTtml(ttml: String): List<LyricLine> = runCatching {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(ttml))
    }
    val lines = ArrayList<LyricLine>()
    var lineStart: Long? = null
    val words = ArrayList<LyricWord>()
    val text = StringBuilder()
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
                    words.lastOrNull()?.takeIf { !it.text.endsWith(' ') }?.let { words[words.lastIndex] = it.copy(text = it.text + " ") }
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

private fun String.plain(): String = lowercase().filter { it.isLetterOrDigit() }

fun alignTo(reference: List<LyricLine>, detailed: List<LyricLine>): List<LyricLine>? {
    val gaps = ArrayList<Long>()
    var from = 0
    for (line in detailed) {
        val text = line.text.plain()
        if (text.length < 4) continue
        val index = (from until minOf(from + 8, reference.size)).firstOrNull { reference[it].text.plain() == text } ?: continue
        gaps += reference[index].startMs - line.startMs
        from = index + 1
    }
    if (gaps.size < maxOf(4, reference.size / 4)) return null
    val shift = gaps.sorted()[gaps.size / 2]
    if (gaps.count { kotlin.math.abs(it - shift) <= 500 } < gaps.size * 0.7) return null
    return detailed.map { line ->
        line.copy(startMs = line.startMs + shift, words = line.words.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) })
    }
}

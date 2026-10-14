package app.ammusic.ui.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.ammusic.R
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.thumbnailAt
import app.ammusic.youtube.tr
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val Width = 1080
private const val Height = 1350
private const val Margin = 84f

suspend fun shareLyrics(context: Context, track: YtTrack, lines: List<String>) {
    val file = withContext(Dispatchers.Default) {
        val cover = track.thumbnail?.let { url ->
            val request = ImageRequest.Builder(context).data(url.thumbnailAt(544)).allowHardware(false).build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }
        val card = drawCard(context, track, lines, cover)
        val folder = File(context.cacheDir, "shared").apply { mkdirs() }
        File(folder, "paroles.png").also { target -> target.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "${track.title} · ${track.artists}")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, tr("Partager les paroles", "Share lyrics")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun drawCard(context: Context, track: YtTrack, lines: List<String>, cover: Bitmap?): Bitmap {
    val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val base = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
    val bold = Typeface.create(base, 700, false)
    val medium = Typeface.create(base, 500, false)

    if (cover != null) {
        val tiny = Bitmap.createScaledBitmap(cover, 12, 15, true)
        val soft = Bitmap.createScaledBitmap(tiny, Width / 8, Height / 8, true)
        canvas.drawBitmap(Bitmap.createScaledBitmap(soft, Width, Height, true), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    } else {
        canvas.drawColor(0xFF2B1F28.toInt())
    }
    val shade = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, Height.toFloat(), intArrayOf(0x59000000, 0x8C000000.toInt(), 0xB3000000.toInt()), null, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, Width.toFloat(), Height.toFloat(), shade)

    val coverSize = 132f
    if (cover != null) {
        val frame = RectF(Margin, Margin, Margin + coverSize, Margin + coverSize)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(frame, 20f, 20f, Path.Direction.CW) })
        canvas.drawBitmap(cover, null, frame, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }
    val textLeft = if (cover != null) Margin + coverSize + 32f else Margin
    val textWidth = (Width - textLeft - Margin).toInt()
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 42f
        typeface = bold
        letterSpacing = -0.01f
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        textSize = 34f
        typeface = medium
    }
    val headerMiddle = Margin + coverSize / 2f
    canvas.drawText(TextUtils.ellipsize(track.title, titlePaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(), textLeft, headerMiddle - 8f, titlePaint)
    canvas.drawText(TextUtils.ellipsize(track.artists, artistPaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(), textLeft, headerMiddle + 40f, artistPaint)

    val lyricsTop = Margin + coverSize + 96f
    val available = Height - lyricsTop - Margin - 70f
    val lyricsWidth = (Width - 2 * Margin).toInt()
    val text = lines.joinToString("\n")
    var size = 76f
    var layout: StaticLayout
    do {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size
            typeface = bold
            letterSpacing = -0.02f
        }
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, lyricsWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .build()
        size -= 4f
    } while (layout.height > available && size > 36f)
    canvas.save()
    canvas.translate(Margin, lyricsTop + ((available - layout.height) / 2f).coerceAtLeast(0f))
    layout.draw(canvas)
    canvas.restore()

    val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 28f
        typeface = medium
    }
    canvas.drawText("AM Music", Margin, Height - Margin + 6f, mark)
    return bitmap
}

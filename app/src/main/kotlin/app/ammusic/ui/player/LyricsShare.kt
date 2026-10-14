package app.ammusic.ui.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
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
private const val Height = 1920
private const val CardMargin = 90f
private const val Padding = 64f
private const val CoverSize = 128f
private const val Radius = 56f

suspend fun renderLyricsCard(context: Context, track: YtTrack, lines: List<String>): Bitmap = withContext(Dispatchers.Default) {
    val cover = track.thumbnail?.let { url ->
        val request = ImageRequest.Builder(context).data(url.thumbnailAt(544)).allowHardware(false).build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }
    drawCard(context, track, lines, cover)
}

fun shareCard(context: Context, card: Bitmap, track: YtTrack) {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "paroles-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, "${track.title} · ${track.artists}")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).forEach { target ->
        context.grantUriPermission(target.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, tr("Partager les paroles", "Share lyrics"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    chooser.clipData = send.clipData
    context.startActivity(chooser)
}

private fun cardColor(cover: Bitmap?): Int {
    if (cover == null) return 0xFF3A1F2C.toInt()
    val palette = Palette.from(cover).generate()
    val picked = palette.getVibrantColor(palette.getMutedColor(palette.getDominantColor(0xFF3A1F2C.toInt())))
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(picked, hsl)
    if (hsl[1] > 0.08f) hsl[1] = hsl[1].coerceIn(0.4f, 0.75f)
    hsl[2] = hsl[2].coerceIn(0.24f, 0.34f)
    return ColorUtils.HSLToColor(hsl)
}

private fun drawCard(context: Context, track: YtTrack, lines: List<String>, cover: Bitmap?): Bitmap {
    val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val base = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
    val tint = cardColor(cover)

    if (cover != null) {
        canvas.drawBitmap(blurred(cover), null, RectF(0f, 0f, Width.toFloat(), Height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
    } else {
        canvas.drawColor(tint)
    }
    canvas.drawRect(
        0f, 0f, Width.toFloat(), Height.toFloat(),
        Paint().apply { shader = LinearGradient(0f, 0f, 0f, Height.toFloat(), 0x40000000, 0x99000000.toInt(), Shader.TileMode.CLAMP) },
    )
    canvas.drawRect(
        0f, 0f, Width.toFloat(), Height.toFloat(),
        Paint().apply { shader = RadialGradient(Width / 2f, Height / 2f, Height * 0.75f, 0x00000000, 0x73000000, Shader.TileMode.CLAMP) },
    )

    canvas.drawRect(0f, 0f, Width.toFloat(), Height.toFloat(), Paint().apply { shader = BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) })

    val cardWidth = Width - 2 * CardMargin
    val inner = (cardWidth - 2 * Padding).toInt()
    val text = lines.joinToString("\n")
    var size = 82f
    var lyrics: StaticLayout
    do {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size
            typeface = base
            fontVariationSettings = "'wght' 760"
            letterSpacing = -0.025f
        }
        lyrics = StaticLayout.Builder.obtain(text, 0, text.length, paint, inner)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()
        size -= 4f
    } while (lyrics.height > 860 && size > 40f)

    val headerGap = 60f
    val footerGap = 72f
    val footerHeight = 34f
    val cardHeight = Padding + CoverSize + headerGap + lyrics.height + footerGap + footerHeight + Padding
    val top = (Height - cardHeight) / 2f
    val card = RectF(CardMargin, top, CardMargin + cardWidth, top + cardHeight)

    canvas.drawRoundRect(card, Radius, Radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        setShadowLayer(70f, 0f, 24f, 0x80000000.toInt())
    })
    canvas.drawRoundRect(card, Radius, Radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(card.left, card.top, card.right, card.bottom, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    })

    val left = card.left + Padding
    var y = card.top + Padding
    if (cover != null) {
        val frame = RectF(left, y, left + CoverSize, y + CoverSize)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(frame, 18f, 18f, Path.Direction.CW) })
        canvas.drawBitmap(cover, null, frame, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }
    val textLeft = if (cover != null) left + CoverSize + 30f else left
    val textWidth = card.right - Padding - textLeft
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 40f
        typeface = base
        fontVariationSettings = "'wght' 620"
        letterSpacing = -0.01f
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBFFFFFFF.toInt()
        textSize = 32f
        typeface = base
        fontVariationSettings = "'wght' 500"
    }
    val middle = y + CoverSize / 2f
    canvas.drawText(TextUtils.ellipsize(track.title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString(), textLeft, middle - 6f, titlePaint)
    canvas.drawText(TextUtils.ellipsize(track.artists, artistPaint, textWidth, TextUtils.TruncateAt.END).toString(), textLeft, middle + 38f, artistPaint)

    y += CoverSize + headerGap
    canvas.save()
    canvas.translate(left, y)
    lyrics.draw(canvas)
    canvas.restore()

    y += lyrics.height + footerGap
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
    listOf(0.55f, 1f, 0.75f).forEachIndexed { index, height ->
        val barLeft = left + index * 13f
        val barTop = y + footerHeight * (1f - height)
        canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + 8f, y + footerHeight), 4f, 4f, barPaint)
    }
    val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt()
        textSize = 30f
        typeface = base
        fontVariationSettings = "'wght' 620"
    }
    canvas.drawText("AM Music", left + 50f, y + footerHeight - 4f, mark)
    return bitmap
}

private fun blurred(cover: Bitmap): Bitmap {
    val cropWidth = (cover.height * Width / Height).coerceAtMost(cover.width)
    val cropped = Bitmap.createBitmap(cover, (cover.width - cropWidth) / 2, 0, cropWidth, cover.height)
    val width = Width / 8
    val height = Height / 8
    val small = Bitmap.createScaledBitmap(cropped, width, height, true)
    val pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)
    val spare = IntArray(pixels.size)
    repeat(3) {
        boxBlur(pixels, spare, width, height, 9, horizontal = true)
        boxBlur(spare, pixels, width, height, 9, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlur(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val span = 2 * radius + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val position = i.coerceIn(0, length - 1)
            return if (horizontal) line * width + position else position * width + line
        }
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val color = source[at(i)]
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        for (i in 0 until length) {
            target[at(i)] = (0xFF shl 24) or ((red / span) shl 16) or ((green / span) shl 8) or (blue / span)
            val leaving = source[at(i - radius)]
            val entering = source[at(i + radius + 1)]
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

private fun grain(): Bitmap {
    val size = 128
    val random = java.util.Random(7)
    val pixels = IntArray(size * size) {
        val value = random.nextInt(256)
        (10 shl 24) or (value shl 16) or (value shl 8) or value
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

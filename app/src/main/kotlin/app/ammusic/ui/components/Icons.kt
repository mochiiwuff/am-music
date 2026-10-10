package app.ammusic.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val Thin = 1.7f

private class Part(val d: String, val fill: Boolean = false, val stroke: Float = Thin, val evenOdd: Boolean = false)

private fun icon(name: String, vararg parts: Part): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        parts.forEach { part ->
            addPath(
                pathData = addPathNodes(part.d),
                fill = if (part.fill) SolidColor(Color.Black) else null,
                stroke = if (part.stroke > 0f) SolidColor(Color.Black) else null,
                strokeLineWidth = part.stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = if (part.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }
    }.build()

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0 Z"

private const val House = "M4 10.4 L12 3.8 L20 10.4 V18.4 A1.6 1.6 0 0 1 18.4 20 H5.6 A1.6 1.6 0 0 1 4 18.4 Z"
private const val Shelf = "M5.1 10 H18.9 A1.6 1.6 0 0 1 20.5 11.6 V18.4 A1.6 1.6 0 0 1 18.9 20 H5.1 A1.6 1.6 0 0 1 3.5 18.4 V11.6 A1.6 1.6 0 0 1 5.1 10 Z"
private const val Lens = "M10.8 4.2 A6.6 6.6 0 1 0 10.8 17.4 A6.6 6.6 0 1 0 10.8 4.2 Z"
private const val HeartShape = "M12 20 C12 20 3.5 15.2 3.5 9.3 A4.4 4.4 0 0 1 12 7.3 A4.4 4.4 0 0 1 20.5 9.3 C20.5 15.2 12 20 12 20 Z"

/** Jeu d'icônes dessiné pour AM Music : traits fins arrondis, versions pleines pour l'état actif. */
object AmIcons {
    val Home by lazy { icon("Home", Part(House), Part("M10 20 V15.4 A2 2 0 0 1 14 15.4 V20")) }
    val HomeFilled by lazy {
        icon("HomeFilled", Part("$House M10.2 20 V15.6 A1.8 1.8 0 0 1 13.8 15.6 V20 Z", fill = true, stroke = 1.2f, evenOdd = true))
    }
    val Search by lazy { icon("Search", Part(Lens), Part("M15.6 15.6 L20 20")) }
    val SearchBold by lazy { icon("SearchBold", Part(Lens, stroke = 2.5f), Part("M15.8 15.8 L20 20", stroke = 2.5f)) }
    val Library by lazy { icon("Library", Part(Shelf), Part("M6.5 6.9 H17.5"), Part("M9 3.9 H15")) }
    val LibraryFilled by lazy {
        icon("LibraryFilled", Part(Shelf, fill = true), Part("M6.5 6.9 H17.5", stroke = 2f), Part("M9 3.9 H15", stroke = 2f))
    }

    val Play by lazy { icon("Play", Part("M8 5.6 L18.4 12 L8 18.4 Z", fill = true, stroke = 2.2f)) }
    val Pause by lazy {
        icon("Pause", Part("M7.6 6 V18", stroke = 3.6f), Part("M16.4 6 V18", stroke = 3.6f))
    }
    val Next by lazy {
        icon("Next", Part("M4.2 7.4 L11.4 12 L4.2 16.6 Z", fill = true, stroke = 1.8f), Part("M12.8 7.4 L20 12 L12.8 16.6 Z", fill = true, stroke = 1.8f))
    }

    val Previous by lazy {
        icon("Previous", Part("M19.8 7.4 L12.6 12 L19.8 16.6 Z", fill = true, stroke = 1.8f), Part("M11.2 7.4 L4 12 L11.2 16.6 Z", fill = true, stroke = 1.8f))
    }
    val Shuffle by lazy {
        icon(
            "Shuffle",
            Part("M4 7.5 H7.2 C9.6 7.5 10.6 9 12 12 C13.4 15 14.4 16.5 16.8 16.5 H20"),
            Part("M4 16.5 H7.2 C8.6 16.5 9.5 15.9 10.3 14.8"),
            Part("M13.7 9.2 C14.5 8.1 15.4 7.5 16.8 7.5 H20"),
            Part("M17.7 5 L20.2 7.5 L17.7 10"),
            Part("M17.7 14 L20.2 16.5 L17.7 19"),
        )
    }
    private const val Loop = "M4 11.6 V10 A3.2 3.2 0 0 1 7.2 6.8 H20 M20 12.4 V14 A3.2 3.2 0 0 1 16.8 17.2 H4"
    private const val LoopArrows = "M17.2 4 L20 6.8 L17.2 9.6 M6.8 20 L4 17.2 L6.8 14.4"
    val Repeat by lazy { icon("Repeat", Part(Loop), Part(LoopArrows)) }
    val RepeatOne by lazy { icon("RepeatOne", Part(Loop), Part(LoopArrows), Part("M11.2 10.7 L12.5 9.9 V14.3", stroke = 1.5f)) }
    val Queue by lazy {
        icon("Queue", Part("M4 6.5 H20"), Part("M4 11.5 H11.5"), Part("M4 16.5 H11.5"), Part("M15.4 11.6 L20.4 14.5 L15.4 17.4 Z", fill = true, stroke = 1.4f))
    }
    val VolumeLow by lazy { icon("VolumeLow", Part("M7 10 H9.6 L13.4 6.8 V17.2 L9.6 14 H7 Z", fill = true, stroke = 1.4f)) }
    val VolumeHigh by lazy {
        icon(
            "VolumeHigh",
            Part("M3.6 10 H6.2 L10 6.8 V17.2 L6.2 14 H3.6 Z", fill = true, stroke = 1.4f),
            Part("M13.6 9.3 A3.8 3.8 0 0 1 13.6 14.7"),
            Part("M16.2 6.8 A7.2 7.2 0 0 1 16.2 17.2"),
        )
    }
    val Grip by lazy { icon("Grip", Part("M5 9.5 H19"), Part("M5 14.5 H19")) }
    val ChevronDown by lazy { icon("ChevronDown", Part("M6 9.5 L12 15.5 L18 9.5", stroke = 2f)) }

    val Heart by lazy { icon("Heart", Part(HeartShape)) }
    val HeartFilled by lazy { icon("HeartFilled", Part(HeartShape, fill = true)) }
    val More by lazy {
        icon("More", Part(circle(6f, 12f, 1.5f), fill = true, stroke = 0f), Part(circle(12f, 12f, 1.5f), fill = true, stroke = 0f), Part(circle(18f, 12f, 1.5f), fill = true, stroke = 0f))
    }
    val ChevronRight by lazy { icon("ChevronRight", Part("M9.5 6 L15.5 12 L9.5 18")) }
    val ChevronLeft by lazy { icon("ChevronLeft", Part("M14.5 6 L8.5 12 L14.5 18", stroke = 2f)) }
    val Close by lazy { icon("Close", Part("M6.5 6.5 L17.5 17.5"), Part("M17.5 6.5 L6.5 17.5")) }

    val User by lazy { icon("User", Part(circle(12f, 8.6f, 3.6f)), Part("M5 19.6 C5 16.6 8 14.9 12 14.9 C16 14.9 19 16.6 19 19.6")) }
    val Playlist by lazy {
        icon("Playlist", Part("M4 7 H13.5"), Part("M4 12 H10.5"), Part("M4 17 H8.5"), Part(circle(14.6f, 17.2f, 2.3f)), Part("M16.9 17.2 V6.6 L20.4 8.2"))
    }
    val Album by lazy { icon("Album", Part(circle(12f, 12f, 8.5f)), Part(circle(12f, 12f, 2.6f))) }
    val Artist by lazy {
        icon("Artist", Part("M9 6.6 A3 3 0 0 1 15 6.6 V11 A3 3 0 0 1 9 11 Z"), Part("M5.8 11 A6.2 6.2 0 0 0 18.2 11"), Part("M12 17.2 V20.5"))
    }
    val Download by lazy { icon("Download", Part(circle(12f, 12f, 8.5f)), Part("M12 7.6 V15.6"), Part("M8.6 12.4 L12 15.8 L15.4 12.4")) }
    val Clock by lazy { icon("Clock", Part(circle(12f, 12f, 8.5f)), Part("M12 7.4 V12 L15.2 13.9")) }

    val PlayNext by lazy {
        icon("PlayNext", Part("M4 4.8 L8 7 L4 9.2 Z", fill = true, stroke = 1.4f), Part("M11.5 7 H20"), Part("M4 12.5 H20"), Part("M4 18 H20"))
    }
    val QueueAdd by lazy {
        icon("QueueAdd", Part("M4 6 H20"), Part("M4 11.5 H20"), Part("M4 14.8 L8 17 L4 19.2 Z", fill = true, stroke = 1.4f), Part("M11.5 17 H20"))
    }
    val Radio by lazy {
        icon(
            "Radio",
            Part(circle(12f, 12f, 1.6f), fill = true, stroke = 0f),
            Part("M8.3 8.3 A5.2 5.2 0 0 0 8.3 15.7"),
            Part("M15.7 8.3 A5.2 5.2 0 0 1 15.7 15.7"),
            Part("M5.6 5.6 A9 9 0 0 0 5.6 18.4"),
            Part("M18.4 5.6 A9 9 0 0 1 18.4 18.4"),
        )
    }
    val Share by lazy {
        icon(
            "Share",
            Part("M12 3.6 V14.2"),
            Part("M8.4 7 L12 3.6 L15.6 7"),
            Part("M7.8 10.6 H6.4 A1.6 1.6 0 0 0 4.8 12.2 V18.6 A1.6 1.6 0 0 0 6.4 20.2 H17.6 A1.6 1.6 0 0 0 19.2 18.6 V12.2 A1.6 1.6 0 0 0 17.6 10.6 H16.2"),
        )
    }
}

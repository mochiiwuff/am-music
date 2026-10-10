package app.ammusic.youtube

import java.util.Locale

val isFrench: Boolean get() = Locale.getDefault().language == "fr"

fun tr(fr: String, en: String): String = if (isFrench) fr else en

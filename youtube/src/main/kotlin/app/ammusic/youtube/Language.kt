package app.ammusic.youtube

import java.util.Locale

var appLanguage: String? = null

val isFrench: Boolean get() = (appLanguage ?: Locale.getDefault().language) == "fr"

fun tr(fr: String, en: String): String = if (isFrench) fr else en

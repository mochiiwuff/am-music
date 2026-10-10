package app.ammusic.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val MaxEntries = 12

@Singleton
class SearchHistory @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(prefs.getString(Key, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(query: String) {
        val clean = query.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        save((listOf(clean) + _entries.value.filterNot { it.equals(clean, ignoreCase = true) }).take(MaxEntries))
    }

    fun remove(query: String) = save(_entries.value - query)

    fun clear() = save(emptyList())

    private fun save(entries: List<String>) {
        _entries.value = entries
        prefs.edit { putString(Key, entries.joinToString("\n")) }
    }

    private companion object {
        const val Key = "history"
    }
}

package com.deniscerri.ytdl.ui.more.settings.search

import android.content.Context
import androidx.core.content.edit

/**
 * Most-recently-used store for the settings search bar.
 * Lives in its own preference file so it never mixes with the user settings that get backed up.
 */
class SettingsSearchHistory(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): List<String> {
        return prefs.getString(KEY_QUERIES, null)
            ?.split(DELIMITER)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    fun add(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return get()

        val updated = mutableListOf(trimmed).apply {
            addAll(get().filterNot { it.equals(trimmed, ignoreCase = true) })
        }.take(MAX_ENTRIES)

        return save(updated)
    }

    fun remove(query: String) = save(get().filterNot { it.equals(query, ignoreCase = true) })

    fun clear() = save(emptyList())

    private fun save(queries: List<String>): List<String> {
        prefs.edit { putString(KEY_QUERIES, queries.joinToString(DELIMITER)) }
        return queries
    }

    companion object {
        private const val PREFS_NAME = "settings_search_history"
        private const val KEY_QUERIES = "queries"
        private const val DELIMITER = "\n"
        private const val MAX_ENTRIES = 8
    }
}

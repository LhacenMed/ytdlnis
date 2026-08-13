package com.deniscerri.ytdl.util.extractors.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches the lyrics of a song, from the sources that publish them.
 *
 * The sources are asked one after another rather than all at once, which is the opposite of
 * how the catalogues are consulted, and for a reason: a lyrics fetch has nothing to rank. The
 * first source that has the song has the answer, so asking the next one is only worth it once
 * the previous had nothing, and the usual case costs a single request to [LrclibProvider].
 *
 * A source that fails is a source that answered with nothing: one being down is not a reason
 * to fail a lookup the next one can serve, and a song with no lyrics anywhere is a normal
 * outcome that leaves the field empty for the user to fill in themselves.
 */
object LyricsUtil {

    /**
     * The sources, in the order they are asked. This order is the whole preference: the first
     * one that answers wins. Adding a source is adding it here.
     */
    private val providers = listOf(LrclibProvider, NeteaseProvider)

    /** Id to brand name, in the order above. Fills the source picker of the manual search. */
    val sources: List<Pair<String, String>> = providers.map { it.id to it.name }

    /** A timestamp tag, which is what separates lyrics that follow the song from plain text. */
    private val TIMESTAMP = Regex("""\[\d{1,2}:\d{2}""")

    fun isTimed(lyrics: String): Boolean = TIMESTAMP.containsMatchIn(lyrics)

    /** The lines that carry something, which is what the card reports having. */
    fun lineCount(lyrics: String): Int = lyrics.lineSequence().count { it.isNotBlank() }

    /**
     * @param sourceId narrows the fetch to a single source, null asks them all in order.
     * @return the lyrics, or null when no source had any.
     */
    suspend fun fetch(artist: String, title: String, sourceId: String? = null): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null

            providers
                .filter { sourceId == null || it.id == sourceId }
                .firstNotNullOfOrNull { source ->
                    runCatching { source.fetch(artist, title) }.getOrNull()?.ifBlank { null }
                }
        }
}

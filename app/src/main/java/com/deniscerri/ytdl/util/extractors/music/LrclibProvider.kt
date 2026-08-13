package com.deniscerri.ytdl.util.extractors.music

import com.google.gson.JsonObject

/**
 * LRCLIB, the primary lyrics source: an open database built for timed lyrics, with no key and
 * no quota, which is why it is asked before anything else.
 *
 * Two endpoints, in the order they are worth asking. The exact one answers when the track and
 * the artist are named the way the database has them, which is the common case for a song a
 * catalogue just resolved. The search endpoint is what catches the rest, and its results are
 * read timed ones first: a plain copy is only worth taking when no version of the song has
 * timings at all.
 */
object LrclibProvider : LyricsProvider {

    private const val API = "https://lrclib.net/api"

    override val id = "lrclib"
    override val name = "LRCLIB"

    override suspend fun fetch(artist: String, title: String): String? {
        val query = "track_name=${MusicHttp.encode(title)}&artist_name=${MusicHttp.encode(artist)}"

        MusicHttp.json("$API/get?$query")?.lyrics()?.let { return it }

        val results = MusicHttp.jsonArray("$API/search?$query")?.map { it.asJsonObject } ?: return null
        return results.firstNotNullOfOrNull { it.timed() } ?: results.firstNotNullOfOrNull { it.plain() }
    }

    /** What the record has, timed for preference. */
    private fun JsonObject.lyrics(): String? = timed() ?: plain()

    private fun JsonObject.timed(): String? = str("syncedLyrics").ifBlank { null }

    private fun JsonObject.plain(): String? = str("plainLyrics").ifBlank { null }
}

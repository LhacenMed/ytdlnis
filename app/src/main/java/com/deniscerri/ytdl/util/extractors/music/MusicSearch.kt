package com.deniscerri.ytdl.util.extractors.music

/**
 * One manual lookup, as the search dialog describes it.
 *
 * The song and the lyrics are searched by the same request because the user asks for them in
 * the same breath, but they stay two decisions: a null id means "every source", and lyrics
 * can be left out of the lookup entirely without that saying anything about the song.
 */
data class MusicSearch(
    val artist: String,
    val song: String,
    /** The catalogue to resolve the tags from, null for all of them. */
    val catalogueId: String? = null,
    val withLyrics: Boolean = true,
    /** The source to fetch the lyrics from, null for all of them. */
    val lyricsSourceId: String? = null
)

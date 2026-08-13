package com.deniscerri.ytdl.util.extractors.music

/**
 * A source the lyrics of a song can be fetched from.
 *
 * Deliberately narrower than [MusicProvider]: lyrics are one value with nothing to complete
 * afterwards, so a source is asked once and answers with the whole of what it has.
 *
 * Timed lyrics are the point of this, so a source that has both returns the timed ones. Null
 * is "this source has nothing for that song", which is the normal outcome for a source that
 * simply never indexed it, and the reason the next one is asked.
 *
 * Suspending because it hits the network, and free to block: [LyricsUtil] is what decides
 * where the fetch runs.
 */
interface LyricsProvider {

    /** Matches what the search dialog offers, so a chosen source is found again by id. */
    val id: String

    /** Brand name, shown when the manual search lets the user aim at one source. */
    val name: String

    /** @return the lyrics, timed where the source has them, or null when it has none. */
    suspend fun fetch(artist: String, title: String): String?
}

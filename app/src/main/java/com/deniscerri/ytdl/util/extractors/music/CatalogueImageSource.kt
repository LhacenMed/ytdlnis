package com.deniscerri.ytdl.util.extractors.music

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Cover art straight from the music catalogues, which is what a cover search is usually after.
 *
 * A web search finds pictures of a song, these publish the release itself: the same artwork
 * the streaming services show, at the size their own apps download it at. That is both the
 * right image and the largest one there is of it, so it leads the results.
 *
 * The catalogues are the ones the metadata lookup already uses, asked here for their artwork
 * instead of their tags. Both are asked at once, and either one saying nothing is normal.
 */
object CatalogueImageSource {

    /** Enough releases to cover a song, its album and the versions of both. */
    private const val LIMIT = 12

    /**
     * A search engine query names the service it expects the cover from, which a catalogue
     * cannot match on: Deezer answers nothing at all for a query carrying one.
     */
    private val SERVICE_NAMES = Regex(
        """\b(spotify|apple music|itunes|deezer|tidal|soundcloud|youtube music|youtube|amazon music)\b""",
        RegexOption.IGNORE_CASE
    )

    private val EXTRA_SPACES = Regex("""\s{2,}""")

    /** @return the artwork of everything the catalogues matched, largest size first. */
    suspend fun search(query: String): List<CoverImage> = coroutineScope {
        val cleaned = EXTRA_SPACES.replace(SERVICE_NAMES.replace(query, ""), " ").trim()
        if (cleaned.isBlank()) return@coroutineScope emptyList()

        listOf(ItunesProvider, DeezerProvider)
            .map { catalogue -> async { catalogue.search(cleaned, LIMIT).orEmpty() } }
            .awaitAll()
            .flatten()
            .mapNotNull { artwork(it.coverUrl) }
    }

    /**
     * Every catalogue serves its artwork at a size written into the url, so the largest copy
     * and a thumbnail of it are both a rewrite away, with no request to find out either.
     */
    private class Artwork(
        val host: String,
        val size: Regex,
        val full: String,
        val thumbnail: String,
        val pixels: Int
    )

    private val ARTWORK = listOf(
        //Apple stops at 3000, and serves 3000 for anything asked above it
        Artwork("mzstatic.com", Regex("""/\d+x\d+bb\.jpg$"""), "/3000x3000bb.jpg", "/300x300bb.jpg", 3000),
        Artwork("dzcdn.net", Regex("""/\d+x\d+-"""), "/1000x1000-", "/250x250-", 1000)
    )

    /**
     * @return the cover at its full size, or null when it came from somewhere unknown. Cover
     * art is square and every catalogue serves it at the size asked for, so what it measures
     * is known without ever fetching it.
     */
    private fun artwork(coverUrl: String): CoverImage? {
        if (coverUrl.isBlank()) return null
        val artwork = ARTWORK.firstOrNull { it.host in coverUrl } ?: return null
        return CoverImage(
            url = artwork.size.replace(coverUrl, artwork.full),
            thumbnailUrl = artwork.size.replace(coverUrl, artwork.thumbnail),
            width = artwork.pixels,
            height = artwork.pixels
        )
    }
}

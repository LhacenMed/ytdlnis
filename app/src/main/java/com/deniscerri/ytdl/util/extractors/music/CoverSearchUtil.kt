package com.deniscerri.ytdl.util.extractors.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * One image result: [url] is the original file, the one an embed gets, and [thumbnailUrl] is
 * the small copy the grid is drawn with, so browsing results costs a thumbnail each and the
 * full image is only ever fetched for the one that gets picked.
 */
data class CoverImage(
    val url: String,
    val thumbnailUrl: String,
    val width: Int,
    val height: Int
) {
    /** "3000 × 3000", how the user tells the results apart by quality. */
    val size: String get() = "$width × $height"
}

/**
 * Searches images for whatever the user typed, and puts the covers of the song they typed it
 * for on top.
 *
 * The two sources answer different halves of the same question. [BingImageSource] is the
 * search itself: any query, any subject, the results a search engine ranks for it.
 * [CatalogueImageSource] recognises a song in the query and answers with the release itself,
 * the same artwork the streaming services show, at a size no web result comes close to, which
 * is what a cover search is nearly always after and why it leads.
 *
 * A query the catalogues make nothing of costs nothing: they answer with no covers and the
 * search results stand on their own. Both are asked at once, so a search costs the slower of
 * the two rather than their sum, and each keeps its own ranking.
 *
 * Google is not among the sources. It answers an app the way it answers a robot, with a
 * captcha rather than results, whether asked over http or through a browser of our own.
 */
object CoverSearchUtil {

    /** More than a couple of screens of results is scrolling nobody does for a cover. */
    private const val MAX_RESULTS = 40

    /** @return the covers first, the search results after them, or empty when there are none. */
    suspend fun search(query: String): List<CoverImage> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val covers = async { CatalogueImageSource.search(query) }
        val web = async { BingImageSource.search(query) }

        (covers.await() + web.await()).distinctBy { it.url }.take(MAX_RESULTS)
    }
}

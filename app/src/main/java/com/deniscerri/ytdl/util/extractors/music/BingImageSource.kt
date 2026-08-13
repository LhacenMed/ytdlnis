package com.deniscerri.ytdl.util.extractors.music

/**
 * Web image results, the ones an image search answers with for anything the user types.
 *
 * Read from the endpoint the results page loads its tiles from rather than the page itself.
 * The page is a shell that fills itself in for some queries and arrives complete for others,
 * so reading it works for a song and returns nothing for a word like "spotify". The tiles
 * come from one address, always in the markup, for every query there is an answer to.
 *
 * Each tile carries the original image and a thumbnail of it, which is exactly what the grid
 * browses with and what a pick returns. No size filtering: the results are what the search
 * answered with, in the order it ranked them.
 */
object BingImageSource {

    /** The tile feed of the results page: one page of results, complete, in one request. */
    private const val SEARCH = "https://www.bing.com/images/async?first=0&count=35&mmasync=1&q="

    /** A desktop browser, which is who the tiles are rendered for. */
    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    /**
     * One tile: the image it points at and its thumbnail, from the attribute it describes
     * itself with, then the size of the original, which the link to its details carries. The
     * span between the two is bounded, so a tile built differently is skipped rather than
     * swallowing the ones after it.
     */
    private val RESULT = Regex(
        """murl&quot;:&quot;(.*?)&quot;,&quot;turl&quot;:&quot;(.*?)&quot;""" +
                """[\s\S]{0,3000}?&amp;exph=(\d{2,5})&amp;expw=(\d{2,5})"""
    )

    /** @return the results in the order they were ranked, or empty when there are none. */
    fun search(query: String): List<CoverImage> {
        val tiles = MusicHttp.html(SEARCH + MusicHttp.encode(query), HEADERS) ?: return emptyList()
        return RESULT.findAll(tiles).mapNotNull { match ->
            val (url, thumbnail, height, width) = match.destructured
            image(unescape(url), unescape(thumbnail), width.toInt(), height.toInt())
        }.toList()
    }

    /** @return the result, or null when the tile named no image to show. */
    private fun image(url: String, thumbnail: String, width: Int, height: Int): CoverImage? {
        if (url.isBlank()) return null
        //a missing thumbnail costs the full image in the grid, never an empty tile
        return CoverImage(url, thumbnail.ifBlank { url }, width, height)
    }

    /** The urls are attributes of the markup, so they arrive escaped as part of it. */
    private fun unescape(url: String): String = url
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("\\/", "/")
}

package com.deniscerri.ytdl.util.extractors.music

import com.deniscerri.ytdl.database.models.MusicMetadata
import java.text.Normalizer

/**
 * What the lookup is actually after: the text sent to the catalogues, plus the rendition the
 * video asked for. The version is kept apart from [title] on purpose, since the searched text
 * is the cleaned song name ("Bad Boy") while the wanted rendition ("remix", or none at all)
 * is what tells a right match from a near one.
 */
data class MusicQuery(
    val artist: String,
    val title: String,
    val version: String = ""
) {
    /** The one line the catalogues are searched with. */
    fun text(): String = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Decides which of the results the catalogues returned is the song that was asked for.
 *
 * A catalogue ranks by its own relevance, which is not the same question: searching
 * "Marwa Loud Bad Boy" answers with "Bad Boy (Remix)" just as happily as with "Bad Boy". So the
 * results of every catalogue are scored against the query here, and the ones that answer it
 * best come first, whichever catalogue they came from.
 *
 * The score is title similarity plus artist similarity, minus a penalty when the rendition is
 * not the one that was wanted. Catalogue order only breaks ties: an equally good match keeps
 * coming from the primary catalogue, a better one elsewhere overtakes it.
 */
object MusicMatcher {

    // ── Weights ────────────────────────────────────────────────────────────────

    private const val TITLE_WEIGHT = 0.6
    private const val ARTIST_WEIGHT = 0.4

    /** The plain song was wanted and this is a rendition of it. */
    private const val UNWANTED_VERSION = 0.35

    /** A rendition was wanted and this is the plain song. */
    private const val MISSING_VERSION = 0.30

    /** A rendition was wanted and this is a different one. */
    private const val WRONG_VERSION = 0.45

    /** Tie-break only: enough to keep catalogue order, never enough to outrank a better match. */
    private const val PROVIDER_STEP = 0.02

    /** One partly contained in the other ("Zemër" vs "Zemër Nena"), still clearly the same song. */
    private const val CONTAINED = 0.9

    /** A top match at least this good answers the query, so no further candidate is tried. */
    const val CONFIDENT = 0.75

    // ── Normalisation ──────────────────────────────────────────────────────────

    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

    private val BRACKETED = Regex("""[\(\[][^\)\]]*[\)\]]""")
    private val FEATURING = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE)

    /** Renditions that are a different recording, so matching the wrong one is matching the wrong song. */
    private const val VERSIONS =
        "remix|live|acoustic|instrumental|karaoke|unplugged|cover|demo|reprise|" +
                "orchestral|piano|nightcore|slowed|sped\\s*up"

    private val TRAILING_VERSION = Regex("""\s*(?:[-–—]\s*)?(?:$VERSIONS)\b.*$""", RegexOption.IGNORE_CASE)
    private val VERSION_WORD = Regex("""\b(?:$VERSIONS)\b""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /** Case, accents and punctuation are noise here: "Zemër" and "zemer" are the same song. */
    fun normalize(value: String): String =
        DIACRITICS.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
            .let { NON_ALPHANUMERIC.replace(it, " ") }
            .trim()
            .lowercase()

    /**
     * The normalised song name alone, without featuring artists, brackets or a version tag.
     *
     * A version word only tags a song when a song is left in front of it, so "Piano Man" keeps
     * its whole name while "Bad Boy (Remix)" is reduced to the song the remix is of.
     */
    fun baseTitle(title: String): String {
        val bare = BRACKETED.replace(FEATURING.replace(title, ""), "")
        return normalize(TRAILING_VERSION.replace(bare, ""))
            .ifBlank { normalize(bare) }
            .ifBlank { normalize(title) }
    }

    /**
     * The rendition a title names, empty for the plain song.
     *
     * Only what trails the song name counts, so a song actually called "Live" is not read as a
     * live recording of itself.
     */
    fun versionOf(title: String): String {
        val tail = normalize(title).removePrefix(baseTitle(title)).trim()
        return VERSION_WORD.find(tail)?.value?.replace(SPACES, " ").orEmpty()
    }

    // ── Scoring ────────────────────────────────────────────────────────────────

    /** How well [candidate] answers [query], from 0 (unrelated) to 1 (exactly the song asked for). */
    fun score(query: MusicQuery, candidate: MusicMetadata): Double {
        val titleScore = similarity(baseTitle(query.title), baseTitle(candidate.title))
        val versionScore = versionPenalty(query.version, versionOf(candidate.title))

        //without an artist to compare, the title carries the whole judgement
        val value = if (query.artist.isBlank()) {
            titleScore
        } else {
            TITLE_WEIGHT * titleScore +
                    ARTIST_WEIGHT * similarity(normalize(query.artist), normalize(candidate.artist))
        }
        return (value - versionScore).coerceIn(0.0, 1.0)
    }

    /**
     * The results of every catalogue, merged into one list ordered by how well each answers
     * [query]. [byProvider] is indexed in catalogue priority order, and the same song coming
     * from several catalogues is kept once, from the one that described it best.
     */
    fun rank(query: MusicQuery, byProvider: List<List<MusicMetadata>>, limit: Int): List<MusicMetadata> =
        byProvider.flatMapIndexed { rank, matches ->
            val bias = PROVIDER_STEP * (byProvider.size - rank)
            matches.map { it to score(query, it) + bias }
        }
            .sortedByDescending { (_, score) -> score }
            .distinctBy { (match, _) -> identity(match) }
            .take(limit)
            .map { (match, _) -> match }

    /** The song a match stands for, so the same one found twice is not offered twice. */
    private fun identity(match: MusicMetadata): String =
        listOf(normalize(match.artist), baseTitle(match.title), versionOf(match.title)).joinToString("|")

    private fun versionPenalty(wanted: String, found: String): Double = when {
        wanted == found -> 0.0
        wanted.isBlank() -> UNWANTED_VERSION
        found.isBlank() -> MISSING_VERSION
        else -> WRONG_VERSION
    }

    /** Edit distance ratio, with containment treated as a near match rather than a partial one. */
    private fun similarity(a: String, b: String): Double = when {
        a == b -> 1.0
        a.isEmpty() || b.isEmpty() -> 0.0
        a.contains(b) || b.contains(a) -> CONTAINED
        else -> 1.0 - distance(a, b).toDouble() / maxOf(a.length, b.length)
    }

    /** Levenshtein over two rows: the strings are song names, so this stays cheap. */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }
}

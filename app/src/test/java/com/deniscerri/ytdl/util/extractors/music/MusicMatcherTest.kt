package com.deniscerri.ytdl.util.extractors.music

import com.deniscerri.ytdl.database.models.MusicMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cases the ranking exists for: a catalogue answering with the wrong rendition of the right
 * song, and the version words that must not be mistaken for one.
 */
class MusicMatcherTest {

    private fun song(artist: String, title: String) = MusicMetadata(title = title, artist = artist)

    @Test
    fun `the wanted rendition outranks the one the catalogue offered first`() {
        val query = MusicQuery("Marwa Loud", "Bad Boy")
        val deezer = listOf(song("Marwa Loud", "Bad Boy (Remix)"))
        val itunes = listOf(song("Marwa Loud", "Bad Boy"))

        val ranked = MusicMatcher.rank(query, listOf(deezer, itunes), limit = 8)

        assertEquals("Bad Boy", ranked.first().title)
    }

    @Test
    fun `an equally good match keeps coming from the primary catalogue`() {
        val query = MusicQuery("Marwa Loud", "Bad Boy")
        val ranked = MusicMatcher.rank(
            query,
            listOf(listOf(song("Marwa Loud", "Bad Boy")), listOf(song("Marwa Loud", "Bad Boy"))),
            limit = 8
        )

        assertEquals(1, ranked.size)
    }

    @Test
    fun `a remix is what a remix video asks for`() {
        val query = MusicQuery("Marwa Loud", "Bad Boy", version = "remix")
        val ranked = MusicMatcher.rank(
            query,
            listOf(listOf(song("Marwa Loud", "Bad Boy"), song("Marwa Loud", "Bad Boy (Remix)"))),
            limit = 8
        )

        assertEquals("Bad Boy (Remix)", ranked.first().title)
    }

    @Test
    fun `a version word inside the song name is part of the name`() {
        assertEquals("piano man", MusicMatcher.baseTitle("Piano Man"))
        assertEquals("", MusicMatcher.versionOf("Piano Man"))
        assertEquals("live", MusicMatcher.versionOf("Bad Boy - Live"))
        assertEquals("remix", MusicMatcher.versionOf("Bad Boy (Remix)"))
    }

    @Test
    fun `accents and punctuation do not make it a different song`() {
        val query = MusicQuery("Dhurata Dora", "Zemër")
        assertTrue(MusicMatcher.score(query, song("Dhurata Dora", "Zemer")) >= MusicMatcher.CONFIDENT)
    }
}

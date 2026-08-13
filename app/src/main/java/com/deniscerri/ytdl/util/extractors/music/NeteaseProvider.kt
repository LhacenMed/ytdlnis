package com.deniscerri.ytdl.util.extractors.music

/**
 * NetEase Cloud Music, the second lyrics source: it carries timed lyrics for a lot of what
 * LRCLIB never indexed, non english releases in particular, and answers without a key.
 *
 * Its lyrics live behind a song id, so a fetch is a search followed by a read. The search is
 * asked for a handful of songs rather than one: its own ranking decides which is the song,
 * and the first of them is the one to read.
 */
object NeteaseProvider : LyricsProvider {

    private const val API = "https://music.163.com/api"

    /** Enough for the ranking to be the ranking, few enough to stay one small response. */
    private const val LIMIT = 5

    override val id = "netease"
    override val name = "NetEase"

    override suspend fun fetch(artist: String, title: String): String? {
        val query = MusicHttp.encode(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "))
        val songs = MusicHttp.json("$API/search/get?s=$query&type=1&limit=$LIMIT")
            ?.obj("result")?.arr("songs") ?: return null

        val id = songs.firstOrNull()?.asJsonObject?.str("id")?.ifBlank { null } ?: return null
        //lv asks for the timed lyrics, the only ones worth a request here
        return MusicHttp.json("$API/song/lyric?id=$id&lv=1&kv=1&tv=-1")
            ?.obj("lrc")?.str("lyric")?.ifBlank { null }
    }
}

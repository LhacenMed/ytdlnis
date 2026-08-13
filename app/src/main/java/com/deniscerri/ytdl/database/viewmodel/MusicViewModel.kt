package com.deniscerri.ytdl.database.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.util.extractors.music.LyricsUtil
import com.deniscerri.ytdl.util.extractors.music.MusicMetadataUtil
import com.deniscerri.ytdl.util.extractors.music.MusicSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns music mode for a single download card: whether it is on, and the lookup behind it. The
 * picked result itself lives on the download item, this only drives the switch and the search.
 *
 * Both the card and the sheet read from here, which is why it is scoped to the activity: the
 * button that turns music mode on sits in the sheet, the fields it fills sit in the audio tab,
 * and neither may hold a different opinion about the same song.
 *
 * The card is shown before the video info is fetched, so the lookup follows the video data: it
 * waits while the title is still unknown and re-runs whenever the title changes, whether that
 * is the fetch landing or the user typing one in themselves, unless the user already made the
 * result their own.
 *
 * A search only returns what the catalogue could answer in one request. The extended tags are
 * completed afterwards, for the shown match alone, and the state is re-emitted when they land
 * so the card fills itself in without ever blocking the result the user is already reading.
 */
class MusicViewModel : ViewModel() {

    sealed class SearchState {
        data object Idle : SearchState()
        /** The video info is not fetched yet, so there is nothing to search for. */
        data object Waiting : SearchState()
        data object Loading : SearchState()
        /** [matches] ranked by the catalogue, [selected] is the one the card shows. */
        data class Found(val matches: List<MusicMetadata>, val selected: Int = 0) : SearchState()
        data object NotFound : SearchState()
        /** No catalogue could be reached, the only state a retry can do something about. */
        data object Failed : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var lyricsJob: Job? = null
    private var lastQuery: String? = null

    /**
     * Which sources the lyrics come from, as the last search asked for them. The automatic
     * lookup asks for all of them, so a song resolved on its own arrives with its lyrics.
     */
    private var lyricsSearch = MusicSearch(artist = "", song = "")

    /**
     * The lookup a result belongs to. A completion and a lyrics fetch run alongside each other
     * and both replace the result they were started for, so what makes one of them stale is a
     * newer search, not the other one landing first.
     */
    private var generation = 0

    /** The lookup that produced the current state, so a retry repeats exactly it. */
    private var lastSearch: (suspend () -> List<MusicMetadata>?)? = null

    /** Matches already completed, so picking one back costs nothing. */
    private val detailed = mutableSetOf<Int>()

    /** Matches whose lyrics were already fetched, for the same reason. */
    private val lyricsFetched = mutableSetOf<Int>()

    /** Set once the user searches, picks or edits a result, so syncs stop overwriting it. */
    private var pinned = false

    /** Music mode itself, switched from the sheet and rendered by both it and the card. */
    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    /**
     * Keeps the lookup in sync with the video info, whenever it becomes available or changes.
     *
     * Debounced, because the title it follows is also an editable field: typing one in by hand
     * must cost one lookup, not one per keystroke.
     */
    fun syncWithVideo(title: String, uploader: String, url: String) {
        if (pinned) return

        if (title.isBlank() || title == url) {
            searchJob?.cancel()
            lastQuery = null
            _state.value = SearchState.Waiting
            return
        }

        val query = "$title|$uploader"
        if (query == lastQuery) return
        lastQuery = query
        launchSearch(TYPING_DELAY) { MusicMetadataUtil.searchFromVideo(title, uploader) }
    }

    /** Manual lookup with a user supplied artist and song name, aimed as the dialog asked. */
    fun search(request: MusicSearch) {
        pin()
        lyricsSearch = request
        launchSearch { MusicMetadataUtil.search(request.artist, request.song, request.catalogueId) }
    }

    /** Runs the lookup that failed once more, on the user asking for it. */
    fun retry() {
        val search = lastSearch ?: return
        launchSearch(search = search)
    }

    /** Shows another of the matches, completing its extended tags on the way in. */
    fun select(index: Int) {
        val found = _state.value as? SearchState.Found ?: return
        if (index == found.selected || index !in found.matches.indices) return
        pin()
        _state.value = found.copy(selected = index)
        loadDetails(index)
        loadLyrics(index)
    }

    /**
     * Protects the current result from being replaced. Also drops a pending completion: once
     * the user is typing, their fields are the truth and nothing may write over them.
     */
    fun pin() {
        pinned = true
        detailsJob?.cancel()
        lyricsJob?.cancel()
    }

    fun reset() {
        searchJob?.cancel()
        detailsJob?.cancel()
        lyricsJob?.cancel()
        detailed.clear()
        lyricsFetched.clear()
        generation++
        lastQuery = null
        lastSearch = null
        lyricsSearch = MusicSearch(artist = "", song = "")
        pinned = false
        _state.value = SearchState.Idle
    }

    private fun launchSearch(delayMillis: Long = 0, search: suspend () -> List<MusicMetadata>?) {
        searchJob?.cancel()
        detailsJob?.cancel()
        lyricsJob?.cancel()
        detailed.clear()
        lyricsFetched.clear()
        generation++
        lastSearch = search
        _state.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val matches = search()
            _state.value = when {
                matches == null -> SearchState.Failed
                matches.isEmpty() -> SearchState.NotFound
                else -> SearchState.Found(matches)
            }
            if (matches?.isNotEmpty() == true) {
                loadDetails(0)
                loadLyrics(0)
            }
        }
    }

    /** Replaces one match with its completed form, leaving the rest of the result list alone. */
    private fun loadDetails(index: Int) {
        if (index in detailed) return
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            val started = generation
            val found = _state.value as? SearchState.Found ?: return@launch
            val completed = MusicMetadataUtil.details(found.matches[index])

            detailed += index
            replaceMatch(started, index) { completed.copy(lyrics = it.lyrics) }
        }
    }

    /**
     * Fills in the lyrics of one match, alongside its completion rather than after it: the two
     * ask different services and neither is worth making the other wait for.
     *
     * A match that already carries lyrics keeps them, which is what makes a fetch land once:
     * they are the one tag long enough that rewriting it under the user would be noticed.
     */
    private fun loadLyrics(index: Int) {
        if (!lyricsSearch.withLyrics || index in lyricsFetched) return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val started = generation
            val found = _state.value as? SearchState.Found ?: return@launch
            val match = found.matches[index]
            if (match.lyrics.isNotBlank()) return@launch

            val lyrics = LyricsUtil.fetch(match.artist, match.title, lyricsSearch.lyricsSourceId)
                ?: return@launch

            lyricsFetched += index
            replaceMatch(started, index) { it.copy(lyrics = lyrics) }
        }
    }

    /**
     * Puts one match back into the current result, as [update] leaves it. Does nothing once a
     * newer search owns the state: what it would replace is no longer the song being shown.
     */
    private fun replaceMatch(generation: Int, index: Int, update: (MusicMetadata) -> MusicMetadata) {
        if (generation != this.generation) return
        val current = _state.value as? SearchState.Found ?: return
        if (index !in current.matches.indices) return
        _state.value = current.copy(
            matches = current.matches.toMutableList().also { it[index] = update(it[index]) }
        )
    }

    private companion object {
        /** Long enough to let a typed title settle, short enough to feel like it reacted. */
        const val TYPING_DELAY = 600L
    }
}

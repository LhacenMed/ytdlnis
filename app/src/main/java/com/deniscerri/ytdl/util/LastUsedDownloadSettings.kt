package com.deniscerri.ytdl.util

import android.content.SharedPreferences
import androidx.core.content.edit
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.google.gson.Gson

/**
 * Remembers how the user configured their last download of each type and seeds the next one
 * with it, so the download card opens in the state it was left in.
 *
 * Stored as one snapshot per [DownloadType], plus the type the card was last left on, so it
 * also reopens on that tab. Both are written by [remember] alone, from the card itself, as soon
 * as a tab stops being the one on screen: a configuration sticks whether or not a download
 * followed it, and the tab the user walked away from is the tab they come back to.
 *
 * Only card level choices are kept: values that belong to a single video (cut sections, crop,
 * resolved music tags) and values that are a projection of the app settings (the filename
 * template) are deliberately left out, so the settings screens stay authoritative over them.
 */
object LastUsedDownloadSettings {
    private const val SNAPSHOT_KEY = "last_used_settings_"
    private const val TYPE_KEY = "last_used_download_type"
    private const val MUSIC_MODE_KEY = "last_used_music_mode"
    private const val INCOGNITO_KEY = "last_used_incognito"
    private val gson = Gson()

    private data class Snapshot(
        val website: String = "",
        val container: String = "",
        val downloadPath: String = "",
        val formatId: String = "",
        val saveThumb: Boolean = false,
        val audio: AudioPreferences? = null,
        val video: VideoPreferences? = null
    )

    /**
     * Keeps [item] as the starting point of the next download: its configuration, and its type
     * as the tab to reopen on.
     *
     * Called from the card itself whenever a tab stops being the one on screen, so a choice is
     * kept the moment it is made rather than only when a download follows it. Types that carry
     * no snapshot, like a command download, are left alone.
     */
    fun remember(preferences: SharedPreferences, item: DownloadItem) {
        if (item.type != DownloadType.audio && item.type != DownloadType.video) return
        val snapshot = Snapshot(
            website = item.website,
            container = item.container,
            downloadPath = item.downloadPath,
            formatId = item.format.format_id,
            saveThumb = item.SaveThumb,
            audio = if (item.type == DownloadType.audio) {
                //music mode is the sheet's own toggle, remembered on its own above
                item.audioPreferences.copy(musicMetadata = null, musicMode = false)
            } else null,
            video = if (item.type == DownloadType.video) {
                item.videoPreferences.copy(cropValues = "")
            } else null
        )
        preferences.edit {
            putString(SNAPSHOT_KEY + item.type, gson.toJson(snapshot))
            putString(TYPE_KEY, item.type.toString())
        }
    }

    // ── The sheet's own toggles ────────────────────────────────────────────────
    //
    // Music mode and incognito belong to the card rather than to one of its tabs, and a single
    // boolean each. Routing them through a tab snapshot made them depend on that tab's view
    // existing and its lifecycle reaching a save, which is a lot of timing for a switch the
    // user simply left on, so they are written the moment they are switched instead.

    fun rememberMusicMode(preferences: SharedPreferences, enabled: Boolean) =
        preferences.edit { putBoolean(MUSIC_MODE_KEY, enabled) }

    fun lastMusicMode(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(MUSIC_MODE_KEY, false)

    fun rememberIncognito(preferences: SharedPreferences, enabled: Boolean) =
        preferences.edit { putBoolean(INCOGNITO_KEY, enabled) }

    /** [default] is the app setting, which stands until the card is switched out of it once. */
    fun lastIncognito(preferences: SharedPreferences, default: Boolean): Boolean =
        preferences.getBoolean(INCOGNITO_KEY, default)

    /** Seeds a freshly created item with the last used configuration of its type. */
    fun apply(preferences: SharedPreferences, item: DownloadItem) {
        val snapshot = read(preferences, item.type) ?: return

        item.container = snapshot.container
        item.SaveThumb = snapshot.saveThumb
        snapshot.downloadPath.takeIf { it.isNotBlank() }?.let { item.downloadPath = it }

        when (item.type) {
            DownloadType.audio -> snapshot.audio?.let { item.audioPreferences = it.copy() }
            DownloadType.video -> snapshot.video?.let { item.videoPreferences = it.copy(
                audioFormatIDs = item.keepKnownFormats(it.audioFormatIDs)
                    .ifEmpty { item.videoPreferences.audioFormatIDs }
            ) }
            else -> {}
        }

        rememberedFormat(preferences, item, item.allFormats)?.let { item.format = it }
    }

    /**
     * The last used format, but only when it exists in [formats] and comes from the same site,
     * since a format id only means the same thing on the site it came from.
     *
     * Formats are usually fetched after the card is built, so this is also used once they land.
     */
    fun rememberedFormat(preferences: SharedPreferences, item: DownloadItem, formats: List<Format>): Format? {
        val snapshot = read(preferences, item.type) ?: return null
        if (snapshot.website != item.website || snapshot.formatId.isBlank()) return null
        return formats.firstOrNull { it.format_id == snapshot.formatId }
    }

    /** The type of the last committed download, null while there is none to fall back on. */
    fun lastType(preferences: SharedPreferences): DownloadType? = runCatching {
        DownloadType.valueOf(preferences.getString(TYPE_KEY, "")!!)
            .takeIf { it != DownloadType.auto }
    }.getOrNull()

    /** A snapshot written by an older version may no longer parse, the defaults then stand. */
    private fun read(preferences: SharedPreferences, type: DownloadType): Snapshot? = runCatching {
        gson.fromJson(preferences.getString(SNAPSHOT_KEY + type, null), Snapshot::class.java)
    }.getOrNull()

    private fun DownloadItem.keepKnownFormats(ids: List<String>) =
        ArrayList(ids.filter { id -> allFormats.any { it.format_id == id } })
}

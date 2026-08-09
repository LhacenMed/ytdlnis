package com.deniscerri.ytdl.util

import android.content.SharedPreferences
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.VideoPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the card was left configured with has to survive the trip through preferences, or it
 * silently reopens on the app defaults. The sheet's own switches are the cheapest thing to lose
 * that way: one boolean each, that the user simply expects to still be set next time.
 */
class LastUsedDownloadSettingsTest {

    private fun audioItem(musicMode: Boolean) = DownloadItem(
        id = 0, url = "u", title = "t", author = "a", thumb = "", duration = "",
        type = DownloadType.audio, format = Format(), container = "mp3",
        downloadSections = "", allFormats = mutableListOf(), downloadPath = "/music",
        website = "youtube.com", downloadSize = "", playlistTitle = "",
        audioPreferences = AudioPreferences(musicMode = musicMode),
        videoPreferences = VideoPreferences(),
        extraCommands = "", customFileNameTemplate = "", SaveThumb = false,
        status = "Queued", downloadStartTime = 0, logID = null
    )

    @Test
    fun `music mode is remembered on its own, without a tab having to save it`() {
        val preferences = FakePreferences()
        LastUsedDownloadSettings.rememberMusicMode(preferences, true)

        assertTrue(LastUsedDownloadSettings.lastMusicMode(preferences))
    }

    @Test
    fun `incognito is remembered, and the app setting stands until it is`() {
        val preferences = FakePreferences()
        assertTrue(LastUsedDownloadSettings.lastIncognito(preferences, default = true))

        LastUsedDownloadSettings.rememberIncognito(preferences, false)
        assertFalse(LastUsedDownloadSettings.lastIncognito(preferences, default = true))
    }

    @Test
    fun `the configuration and the type the card was left on survive together`() {
        val preferences = FakePreferences()
        LastUsedDownloadSettings.remember(preferences, audioItem(musicMode = true))

        val fresh = audioItem(musicMode = false)
        LastUsedDownloadSettings.apply(preferences, fresh)

        assertEquals("mp3", fresh.container)
        assertEquals("/music", fresh.downloadPath)
        assertEquals(DownloadType.audio, LastUsedDownloadSettings.lastType(preferences))
    }
}

/** Only what [LastUsedDownloadSettings] actually reads and writes. */
private class FakePreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            values[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            values[key!!] = value
            return this
        }

        override fun apply() {}
        override fun commit(): Boolean = true
        override fun clear(): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
    }

    override fun getAll(): MutableMap<String, *> = values
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = defValue
    override fun getLong(key: String?, defValue: Long) = defValue
    override fun getFloat(key: String?, defValue: Float) = defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

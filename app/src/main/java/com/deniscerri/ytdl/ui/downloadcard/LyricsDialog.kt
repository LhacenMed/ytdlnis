package com.deniscerri.ytdl.ui.downloadcard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.extractors.music.LyricsUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

/**
 * Writes and edits the lyrics of the song, whether they were fetched or are being typed from
 * nothing.
 *
 * Lyrics are the one tag too long to edit in the line the card gives every other field, so
 * they get the sheet to themselves: the text fills it, and what is being edited says whether
 * it follows the song or is plain, because that is the difference the user cannot see from
 * the first line alone.
 *
 * Editing is on a copy. The card keeps what it has until the edit is saved, so leaving the
 * sheet changes nothing, which is what makes opening it to read them safe.
 */
class LyricsDialog(
    private val context: Context,
    private val onSaved: (lyrics: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val view = LayoutInflater.from(context).inflate(R.layout.sheet_lyrics, null)

    private val input: EditText = view.findViewById<TextInputLayout>(R.id.lyrics_input).editText!!
    private val summary: TextView = view.findViewById(R.id.lyrics_summary)

    init {
        dialog.setContentView(view)

        view.findViewById<MaterialButton>(R.id.lyrics_save).setOnClickListener {
            onSaved(input.text.toString().trim())
            dialog.dismiss()
        }

        //typing over a full song needs the room, so the sheet opens at the height it can have
        dialog.setOnShowListener {
            BottomSheetBehavior.from(view.parent as View).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    /** Opens on [lyrics], the ones the card is currently carrying. */
    fun show(lyrics: String) {
        input.setText(lyrics)
        summary.text = summaryOf(lyrics)
        dialog.show()
    }

    private fun summaryOf(lyrics: String): String = when {
        lyrics.isBlank() -> context.getString(R.string.lyrics_none)
        LyricsUtil.isTimed(lyrics) ->
            context.getString(R.string.lyrics_timed_lines, LyricsUtil.lineCount(lyrics))
        else -> context.getString(R.string.lyrics_lines, LyricsUtil.lineCount(lyrics))
    }
}

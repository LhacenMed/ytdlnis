package com.deniscerri.ytdl.ui.downloadcard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.adapter.CoverSearchAdapter
import com.deniscerri.ytdl.util.extractors.music.CoverSearchUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Looks up album artwork on the web and shows what it finds as a grid.
 *
 * Opens on the song the card resolved, already searched for: the cover of a known track is
 * one tap away, and the query stays editable for the times it is not the cover the user meant.
 *
 * A pick is handed back rather than applied. The sheet underneath owns the cover and already
 * asks for a confirmation before replacing it, so this one only ever answers "that one".
 */
class CoverSearchDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onImagePicked: (url: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val view = LayoutInflater.from(context).inflate(R.layout.sheet_cover_search, null)

    private val queryInput: EditText = view.findViewById<TextInputLayout>(R.id.cover_search_query).editText!!
    private val results: RecyclerView = view.findViewById(R.id.cover_search_results)
    private val shimmer: ShimmerFrameLayout = view.findViewById(R.id.cover_search_shimmer)
    private val empty: TextView = view.findViewById(R.id.cover_search_empty)

    private val adapter = CoverSearchAdapter { image ->
        onImagePicked(image.url)
        dialog.dismiss()
    }

    /** The running search, replaced whenever the user asks for another one. */
    private var searchJob: Job? = null

    /** What the grid is showing, so reopening on the same song costs nothing. */
    private var shownQuery = ""

    init {
        dialog.setContentView(view)
        results.layoutManager = GridLayoutManager(context, COLUMNS)
        results.adapter = adapter
        results.setHasFixedSize(true)

        view.findViewById<TextInputLayout>(R.id.cover_search_query)
            .setEndIconOnClickListener { search(queryInput.text.toString()) }

        queryInput.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            search(queryInput.text.toString())
            true
        }

        //a grid is browsed, not peeked at, so it opens at the full height it can have
        dialog.setOnShowListener {
            BottomSheetBehavior.from(view.parent as View).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        //a closed sheet searches for nothing
        dialog.setOnDismissListener { searchJob?.cancel() }
    }

    /** Opens on [query], the song as the card knows it, and searches it unless it is already shown. */
    fun show(query: String) {
        if (query != shownQuery) {
            queryInput.setText(query)
            search(query)
        }
        dialog.show()
    }

    private fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        shownQuery = trimmed
        hideKeyboard()
        searchJob?.cancel()
        showLoading()

        searchJob = scope.launch {
            val images = CoverSearchUtil.search(trimmed)
            adapter.submitList(images)
            showResults(images.isEmpty())
        }
    }

    /** The results are what the user is here for, so searching gives the grid the whole sheet. */
    private fun hideKeyboard() {
        queryInput.clearFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(queryInput.windowToken, 0)
    }

    /** The grid sweeps where its results are about to be, so the wait has the shape of them. */
    private fun showLoading() {
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
        results.visibility = View.GONE
        empty.visibility = View.GONE
    }

    private fun showResults(isEmpty: Boolean) {
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
        results.visibility = if (isEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    companion object {
        /** Three across: wide enough to judge a cover by, narrow enough to compare them. */
        private const val COLUMNS = 3
    }
}

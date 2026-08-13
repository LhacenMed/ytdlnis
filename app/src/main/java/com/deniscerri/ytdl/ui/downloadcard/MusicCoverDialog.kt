package com.deniscerri.ytdl.ui.downloadcard

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.util.MusicCoverUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shows the album cover at a size worth looking at, and lets the user replace it with a
 * picture from the device or one found on the web.
 *
 * Picking only previews: the choice is not the users until they confirm it, so the cover on
 * the card stays untouched while they look at the alternative and change their mind. Both
 * sources land in the same preview, and a web cover is kept as its url, which is what a
 * catalogue cover is too: nothing downstream has to know where the artwork was chosen.
 *
 * Deliberately a plain dialog owned by the audio card rather than a fragment of its own. The
 * card already owns the file picker and the card is what the result is for, so keeping both
 * in one place removes the hand off between fragments entirely: nothing here has a lifecycle
 * that can be stopped, capped or rebuilt while the picker is in front.
 */
class MusicCoverDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPickRequested: () -> Unit,
    private val onCoverConfirmed: (cover: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val view = LayoutInflater.from(context).inflate(R.layout.sheet_music_cover, null)

    private val preview: ShapeableImageView = view.findViewById(R.id.cover_preview)
    private val shimmer: ShimmerFrameLayout = view.findViewById(R.id.cover_shimmer)
    private val confirm: MaterialButton = view.findViewById(R.id.cover_confirm)

    /** Built on first use: searching the web is a detour, most covers never need it. */
    private val searchDialog by lazy {
        CoverSearchDialog(context, scope) { url -> preview(url) }
    }

    /** The previewed image, still uncommitted until [confirm] is pressed. */
    private var picked: String? = null

    /** The song the sheet was opened for, which is what a web search starts from. */
    private var song = MusicMetadata()

    /** Renders the previewed cover, replaced whenever the user points at another one. */
    private var renderJob: Job? = null

    init {
        dialog.setContentView(view)
        view.findViewById<MaterialButton>(R.id.cover_choose).setOnClickListener { onPickRequested() }
        view.findViewById<MaterialButton>(R.id.cover_search_online).setOnClickListener {
            searchDialog.show(searchQuery(song))
        }
        confirm.setOnClickListener {
            val cover = picked ?: return@setOnClickListener
            //a web cover is already a url the download can read, only a device one has to be kept
            val resolved = if (isRemote(cover)) cover else MusicCoverUtil.store(context, Uri.parse(cover))
            resolved?.let(onCoverConfirmed)
            dialog.dismiss()
        }
    }

    /** Opens on [metadata], the song the card is showing, artwork included. */
    fun show(metadata: MusicMetadata) {
        song = metadata
        picked = null
        render(metadata.coverUrl)
        dialog.show()
    }

    /**
     * Shows an image the card picked up from the device. Reopens the sheet when the picker
     * closed it on the way, so a chosen image always lands somewhere the user can confirm it.
     */
    fun preview(uri: Uri) = preview(uri.toString())

    /** Shows a candidate cover, from wherever it was chosen, as the one waiting to be confirmed. */
    private fun preview(cover: String) {
        picked = cover
        render(cover)
        if (!dialog.isShowing) dialog.show()
    }

    /**
     * What the web is asked for: the release as the streaming services list it, which is where
     * the covers worth finding are. Falls back to the song alone while the lookup has not
     * resolved an artist yet, and the query stays the users to correct either way.
     */
    private fun searchQuery(metadata: MusicMetadata): String =
        if (metadata.isUsable) "${metadata.title} By ${metadata.artist} Spotify" else metadata.title

    private fun isRemote(cover: String): Boolean = cover.startsWith("http")

    /**
     * Decoding is off the main thread, so a slow source never holds the sheet open empty. The
     * shimmer covers that gap, and covers it again whenever the user points at another image.
     *
     * What could not be read cannot be confirmed either: an image the sheet failed to show is
     * one the download would fail to embed just the same, so the choice stays open instead.
     */
    private fun render(cover: String) {
        renderJob?.cancel()
        shimmer.showShimmer(true)
        confirm.isEnabled = false
        renderJob = scope.launch {
            val bitmap = MusicCoverUtil.preview(context, cover, PREVIEW_MAX_SIZE)
            if (bitmap == null) preview.setImageResource(R.drawable.ic_music)
            else preview.setImageBitmap(bitmap)
            confirm.isEnabled = picked != null && bitmap != null
            shimmer.hideShimmer()
        }
    }

    companion object {
        /** Longest edge the preview decodes to, comfortably above any screen it is shown on. */
        private const val PREVIEW_MAX_SIZE = 1080
    }
}

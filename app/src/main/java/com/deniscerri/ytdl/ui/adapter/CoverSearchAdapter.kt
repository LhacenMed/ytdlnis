package com.deniscerri.ytdl.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.extractors.music.CoverImage
import com.squareup.picasso.Picasso

/**
 * The image results, as a grid of square tiles.
 *
 * Tiles are drawn from the thumbnails, so a screen of results costs a screen of thumbnails:
 * the full image behind a result is only ever fetched for the one the user picks.
 */
class CoverSearchAdapter(
    private val onImageClicked: (image: CoverImage) -> Unit
) : ListAdapter<CoverImage, CoverSearchAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.cover_image)
        val size: TextView = view.findViewById(R.id.cover_size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.cover_search_item, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.size.text = item.size

        //fit() waits for the tile to be measured, which is what keeps the decode small
        Picasso.get().load(item.thumbnailUrl)
            .fit()
            .centerCrop()
            .error(R.drawable.ic_music)
            .into(holder.image)

        holder.itemView.setOnClickListener { onImageClicked(item) }
    }

    companion object {
        /** The url is the image: two results pointing at the same file are the same result. */
        private val DIFF = object : DiffUtil.ItemCallback<CoverImage>() {
            override fun areItemsTheSame(old: CoverImage, new: CoverImage) = old.url == new.url
            override fun areContentsTheSame(old: CoverImage, new: CoverImage) = old == new
        }
    }
}

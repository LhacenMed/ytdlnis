package com.deniscerri.ytdl.ui.more.settings.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R

class SettingsSearchHistoryAdapter(
    private val onQueryClick: (String) -> Unit,
    private val onQueryRemove: (String) -> Unit
) : ListAdapter<String, SettingsSearchHistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val query: TextView = view.findViewById(R.id.suggestion_text)
        val removeButton: ImageButton = view.findViewById(R.id.set_search_query_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_suggestion_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val query = getItem(position)

        holder.query.text = query
        holder.query.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_restore, 0, 0, 0)
        holder.query.setOnClickListener { onQueryClick(query) }

        holder.removeButton.setImageResource(R.drawable.baseline_close_24)
        holder.removeButton.contentDescription = holder.itemView.context.getString(R.string.Remove)
        holder.removeButton.setOnClickListener { onQueryRemove(query) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }
    }
}

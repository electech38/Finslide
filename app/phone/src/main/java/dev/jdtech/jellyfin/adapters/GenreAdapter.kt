package dev.jdtech.jellyfin.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.models.FilterItem

class GenreAdapter(private val onGenreSelected: (FilterItem) -> Unit) : 
    ListAdapter<FilterItem, GenreAdapter.GenreViewHolder>(GenreDiffCallback()) {

    private var selectedPosition = 0
    
    init {
        setHasStableIds(true) // Cải thiện hiệu suất RecyclerView
    }
    
    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    class GenreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val genreName: TextView = view.findViewById(R.id.genre_name)
        val selectedIndicator: ImageView = view.findViewById(R.id.selected_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_genre, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val genre = getItem(position)
        holder.genreName.text = genre.name
        
        // Hiển thị indicator khi đây là genre được chọn
        holder.selectedIndicator.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener {
            if (selectedPosition != position) {
                val oldPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(oldPosition)
                notifyItemChanged(position)
                onGenreSelected(genre)
            }
        }
    }
    
    fun setSelectedGenre(genreId: String?) {
        val newPosition = if (genreId.isNullOrEmpty()) 0 else currentList.indexOfFirst { it.id == genreId }
        if (newPosition != -1 && newPosition != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = newPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(newPosition)
        }
    }
}

private class GenreDiffCallback : DiffUtil.ItemCallback<FilterItem>() {
    override fun areItemsTheSame(oldItem: FilterItem, newItem: FilterItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: FilterItem, newItem: FilterItem): Boolean {
        return oldItem == newItem
    }
}
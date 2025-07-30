package dev.jdtech.jellyfin.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.size.Scale
import dev.jdtech.jellyfin.databinding.FeaturedBannerItemBinding
import dev.jdtech.jellyfin.models.FindroidItem
import timber.log.Timber

class FeaturedBannerAdapter(
    private val onItemClickListener: (FindroidItem) -> Unit
) : ListAdapter<FindroidItem, FeaturedBannerAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FeaturedBannerItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        val binding: FeaturedBannerItemBinding,
        private val onItemClickListener: (FindroidItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FindroidItem) {
            Timber.d("Binding featured item: ${item.name}")
            
            // Tải backdrop với Coil trực tiếp
            val imageUrl = item.images.backdrop ?: item.images.primary
            binding.featuredPoster.alpha = 0f
            binding.featuredPoster.isVisible = true
            binding.featuredPoster.load(imageUrl) {
                crossfade(300)
                scale(Scale.FILL)
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
                listener(
                    onSuccess = { _, _ ->
                        binding.featuredPoster.animate()
                            .alpha(1f)
                            .setDuration(300L)
                            .start()
                        // Áp dụng hiệu ứng zoom nhẹ
                        binding.featuredPoster.animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .setDuration(8000)
                            .start()
                    },
                    onError = { _, errorResult: ErrorResult ->
                        Timber.log(Log.WARN, errorResult.throwable, "Failed to load backdrop: $imageUrl")
                        binding.featuredPoster.isVisible = false
                    }
                )
            }
            
            binding.root.setOnClickListener {
                onItemClickListener(item)
            }
            
            // Set content description for accessibility
            binding.featuredPoster.contentDescription = "Featured: ${item.name}"
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<FindroidItem>() {
        override fun areItemsTheSame(oldItem: FindroidItem, newItem: FindroidItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FindroidItem, newItem: FindroidItem): Boolean {
            return oldItem.id == newItem.id && 
                   oldItem.name == newItem.name && 
                   oldItem.images.backdrop == newItem.images.backdrop &&
                   oldItem.images.primary == newItem.images.primary
        }
    }
}
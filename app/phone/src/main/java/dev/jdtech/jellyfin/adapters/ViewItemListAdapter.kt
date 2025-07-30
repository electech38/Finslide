package dev.jdtech.jellyfin.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.size.Scale
import dev.jdtech.jellyfin.databinding.BaseItemBinding
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.isDownloaded
import timber.log.Timber
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * Adapter for horizontal list of items in each section
 * Optimized with Coil for better image loading
 */
class ViewItemListAdapter(
    private val onClickListener: (item: FindroidItem) -> Unit,
    private val fixedWidth: Boolean = false,
) : ListAdapter<FindroidItem, ViewItemListAdapter.ItemViewHolder>(DiffCallback) {

    class ItemViewHolder(private var binding: BaseItemBinding, private val parent: ViewGroup) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FindroidItem, fixedWidth: Boolean) {
            binding.itemName.text = if (item is FindroidEpisode) item.seriesName else item.name
            binding.itemCount.visibility =
                if (item.unplayedItemCount?.let { it > 0 } == true) View.VISIBLE else View.GONE
            if (fixedWidth) {
                binding.itemLayout.layoutParams.width =
                    parent.resources.getDimension(CoreR.dimen.overview_media_width).toInt()
                (binding.itemLayout.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 0
            }

            binding.itemCount.text = item.unplayedItemCount?.toString() ?: ""
            binding.playedIcon.isVisible = item.played
            binding.downloadedIcon.isVisible = item.isDownloaded()

            // Tải hình ảnh với cùng cách làm như MoviesFragment
            loadImageOptimized(item)
        }
        
        private fun loadImageOptimized(item: FindroidItem) {
            // Lấy primary image trước, tương tự MovieFragment
            val primaryImage = when (item) {
                is FindroidSeason -> item.images.primary ?: item.images.showPrimary
                else -> item.images.primary
            }
            
            // 1. Thử dùng primary image
            if (primaryImage != null) {
                binding.itemImage.alpha = 0f
                binding.itemImage.isVisible = true
                binding.itemImage.load(primaryImage) {
                    crossfade(300)
                    scale(Scale.FILL)
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    listener(
                        onSuccess = { _, _ ->
                            binding.itemImage.animate()
                                .alpha(1f)
                                .setDuration(300L)
                                .start()
                        },
                        onError = { _, errorResult ->
                            Timber.log(Log.WARN, errorResult.throwable, "Failed to load primary image: $primaryImage")
                            // 2. Nếu primary không tải được, thử dùng backdrop
                            tryLoadBackdropImage(item)
                        }
                    )
                }
            } else {
                // Không có primary, thử backdrop
                tryLoadBackdropImage(item)
            }

            binding.itemImage.contentDescription = item.name
        }
        
        private fun tryLoadBackdropImage(item: FindroidItem) {
            val backdropImage = item.images.backdrop
            if (backdropImage != null) {
                binding.itemImage.alpha = 0f
                binding.itemImage.isVisible = true
                binding.itemImage.load(backdropImage) {
                    crossfade(300)
                    scale(Scale.FILL)
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    listener(
                        onSuccess = { _, _ ->
                            binding.itemImage.animate()
                                .alpha(1f)
                                .setDuration(300L)
                                .start()
                        },
                        onError = { _, errorResult ->
                            Timber.log(Log.WARN, errorResult.throwable, "Failed to load backdrop: $backdropImage")
                            // 3. Nếu cả hai đều không được, ẩn ImageView
                            binding.itemImage.isVisible = false
                        }
                    )
                }
            } else {
                // Không có cả primary và backdrop
                binding.itemImage.isVisible = false
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FindroidItem>() {
        override fun areItemsTheSame(oldItem: FindroidItem, newItem: FindroidItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FindroidItem, newItem: FindroidItem): Boolean {
            return oldItem.name == newItem.name
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        return ItemViewHolder(
            BaseItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            parent,
        )
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener(item)
        }
        holder.bind(item, fixedWidth)
    }
}
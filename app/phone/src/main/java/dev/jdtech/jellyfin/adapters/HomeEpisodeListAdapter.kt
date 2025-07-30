package dev.jdtech.jellyfin.adapters

import android.util.Log
import android.util.TypedValue
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
import dev.jdtech.jellyfin.databinding.HomeEpisodeItemBinding
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.isDownloaded
import timber.log.Timber
import dev.jdtech.jellyfin.core.R as CoreR

class HomeEpisodeListAdapter(private val onClickListener: (item: FindroidItem) -> Unit) : ListAdapter<FindroidItem, HomeEpisodeListAdapter.EpisodeViewHolder>(DiffCallback) {
    class EpisodeViewHolder(
        private var binding: HomeEpisodeItemBinding,
        private val parent: ViewGroup,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FindroidItem) {
            // Progress bar logic
            if (item.playbackPositionTicks > 0 && item.runtimeTicks > 0) {
                binding.progressBar.layoutParams.width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (item.playbackPositionTicks.div(item.runtimeTicks.toFloat()).times(224)),
                    binding.progressBar.context.resources.displayMetrics,
                ).toInt()
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }

            binding.downloadedIcon.isVisible = item.isDownloaded()

            // Text logic - keep original
            when (item) {
                is FindroidMovie -> {
                    binding.primaryName.text = item.name
                    binding.secondaryName.visibility = View.GONE
                }
                is FindroidEpisode -> {
                    binding.primaryName.text = item.seriesName
                    binding.secondaryName.text = if (item.indexNumberEnd == null) {
                        parent.resources.getString(CoreR.string.episode_name_extended, item.parentIndexNumber, item.indexNumber, item.name)
                    } else {
                        parent.resources.getString(CoreR.string.episode_name_extended_with_end, item.parentIndexNumber, item.indexNumber, item.indexNumberEnd, item.name)
                    }
                    binding.secondaryName.visibility = View.VISIBLE
                }
                else -> {
                    binding.primaryName.text = item.name
                    binding.secondaryName.visibility = View.GONE
                }
            }

            // Tải hình ảnh với Coil trực tiếp
            val imageUrl = item.images.primary ?: item.images.backdrop
            binding.episodeImage.alpha = 0f
            binding.episodeImage.isVisible = true
            binding.episodeImage.load(imageUrl) {
                crossfade(300)
                scale(Scale.FILL)
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
                listener(
                    onSuccess = { _, _ ->
                        binding.episodeImage.animate()
                            .alpha(1f)
                            .setDuration(300L)
                            .start()
                    },
                    onError = { _, errorResult: ErrorResult ->
                        Timber.log(Log.WARN, errorResult.throwable, "Failed to load image: $imageUrl")
                        binding.episodeImage.isVisible = false
                    }
                )
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        return EpisodeViewHolder(
            HomeEpisodeItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            parent,
        )
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener(item)
        }
        holder.bind(item)
    }
}
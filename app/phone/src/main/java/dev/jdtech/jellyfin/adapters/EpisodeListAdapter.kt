package dev.jdtech.jellyfin.adapters

import android.text.Html.fromHtml
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.jdtech.jellyfin.databinding.EpisodeItemBinding
import dev.jdtech.jellyfin.databinding.SeasonHeaderBinding
import dev.jdtech.jellyfin.models.EpisodeItem
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.isDownloaded
import timber.log.Timber
import java.util.UUID
import dev.jdtech.jellyfin.core.R as CoreR

private const val ITEM_VIEW_TYPE_HEADER = 0
private const val ITEM_VIEW_TYPE_EPISODE = 1

class EpisodeListAdapter(
    private val onClickListener: (item: FindroidEpisode) -> Unit,
    private var season: FindroidSeason?,
    private val seriesId: UUID
) : ListAdapter<EpisodeItem, RecyclerView.ViewHolder>(DiffCallback) {

    class HeaderViewHolder(private var binding: SeasonHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: EpisodeItem.Header, season: FindroidSeason?) {
            binding.seasonName.text = header.seasonName
            binding.seriesName.text = header.seriesName
            // Load season poster with fallback
            season?.let {
                val imageUrl = it.images.primary ?: it.images.backdrop ?: it.images.showPrimary
                Timber.d("Season Header ${header.seasonName}: primary=${it.images.primary}, backdrop=${it.images.backdrop}, showPrimary=${it.images.showPrimary}")
                if (imageUrl != null) {
                    Timber.d("Loading season poster for ${header.seasonName}")
                    binding.seasonPoster.load(imageUrl) {
                        crossfade(200)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_gallery)
                        scale(coil.size.Scale.FIT)
                    }
                    binding.seasonPoster.isVisible = true
                } else {
                    Timber.d("No poster for ${header.seasonName}, using placeholder")
                    binding.seasonPoster.setImageResource(android.R.drawable.ic_menu_gallery)
                    binding.seasonPoster.isVisible = true
                }
            } ?: run {
                Timber.d("No season object for ${header.seasonName}, using placeholder")
                binding.seasonPoster.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.seasonPoster.isVisible = true
            }
            // Load series banner với logic tương tự
            season?.let {
                val bannerUrl = it.images.backdrop ?: it.images.primary ?: it.images.showPrimary
                Timber.d("Series Banner ${header.seriesName}: backdrop=${it.images.backdrop}, primary=${it.images.primary}, showPrimary=${it.images.showPrimary}")
                if (bannerUrl != null) {
                    Timber.d("Loading series banner for ${header.seriesName}")
                    binding.itemBanner.load(bannerUrl) {
                        crossfade(200)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_gallery)
                        scale(coil.size.Scale.FIT)
                    }
                    binding.itemBanner.isVisible = true
                } else {
                    Timber.d("No banner for ${header.seriesName}, using placeholder")
                    binding.itemBanner.setImageResource(android.R.drawable.ic_menu_gallery)
                    binding.itemBanner.isVisible = true
                }
            } ?: run {
                Timber.d("No season object for ${header.seriesName}, using placeholder")
                binding.itemBanner.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.itemBanner.isVisible = true
            }
        }
    }

    class EpisodeViewHolder(private var binding: EpisodeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: FindroidEpisode) {
            binding.episodeTitle.text = if (episode.indexNumberEnd == null) {
                binding.root.context.getString(CoreR.string.episode_name, episode.indexNumber, episode.name)
            } else {
                binding.root.context.getString(CoreR.string.episode_name_with_end, episode.indexNumber, episode.indexNumberEnd, episode.name)
            }

            binding.episodeOverview.text = fromHtml(episode.overview, 0)

            if (episode.playbackPositionTicks > 0) {
                binding.progressBar.layoutParams.width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (episode.playbackPositionTicks.div(episode.runtimeTicks.toFloat()).times(84)),
                    binding.progressBar.context.resources.displayMetrics,
                ).toInt()
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }

            binding.playedIcon.isVisible = episode.played
            binding.missingIcon.isVisible = episode.missing
            binding.downloadedIcon.isVisible = episode.isDownloaded()

            // Enhanced image loading with fallback
            val imageUrl = episode.images.primary ?: episode.images.backdrop ?: episode.images.showPrimary
            Timber.d("Episode ${episode.name}: primary=${episode.images.primary}, backdrop=${episode.images.backdrop}, showPrimary=${episode.images.showPrimary}")
            if (imageUrl != null) {
                Timber.d("Loading episode image for ${episode.name}")
                binding.episodeImage.load(imageUrl) {
                    crossfade(200)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_gallery)
                    scale(coil.size.Scale.FIT)
                }
                binding.episodeImage.isVisible = true
            } else {
                Timber.d("No image for ${episode.name}, using placeholder")
                binding.episodeImage.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.episodeImage.isVisible = true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<EpisodeItem>() {
        override fun areItemsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_HEADER -> {
                HeaderViewHolder(
                    SeasonHeaderBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false,
                    ),
                )
            }
            ITEM_VIEW_TYPE_EPISODE -> {
                EpisodeViewHolder(
                    EpisodeItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false,
                    ),
                )
            }
            else -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            ITEM_VIEW_TYPE_HEADER -> {
                val item = getItem(position) as EpisodeItem.Header
                (holder as HeaderViewHolder).bind(item, season)
            }
            ITEM_VIEW_TYPE_EPISODE -> {
                val item = getItem(position) as EpisodeItem.Episode
                holder.itemView.setOnClickListener {
                    onClickListener(item.episode)
                }
                (holder as EpisodeViewHolder).bind(item.episode)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is EpisodeItem.Header -> ITEM_VIEW_TYPE_HEADER
            is EpisodeItem.Episode -> ITEM_VIEW_TYPE_EPISODE
        }
    }

    // Update season and notify header to refresh
    fun updateSeason(newSeason: FindroidSeason?) {
        season = newSeason
        notifyItemChanged(0) // Refresh header (position 0)
    }
}
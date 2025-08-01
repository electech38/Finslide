package dev.jdtech.jellyfin.fragments

import android.os.Bundle
import android.text.Html.fromHtml
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.databinding.EpisodeBottomSheetBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.utils.safeNavigate
import dev.jdtech.jellyfin.utils.setIconTintColorAttribute
import dev.jdtech.jellyfin.viewmodels.EpisodeBottomSheetEvent
import dev.jdtech.jellyfin.viewmodels.EpisodeBottomSheetViewModel
import dev.jdtech.jellyfin.viewmodels.PlayerItemsEvent
import dev.jdtech.jellyfin.viewmodels.PlayerViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.DateTime
import timber.log.Timber
import java.text.DateFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import android.R as AndroidR
import com.google.android.material.R as MaterialR
import dev.jdtech.jellyfin.core.R as CoreR
import coil.load
import coil.size.Scale

@AndroidEntryPoint
class EpisodeBottomSheetFragment : BottomSheetDialogFragment() {
    private val args: EpisodeBottomSheetFragmentArgs by navArgs()

    private lateinit var binding: EpisodeBottomSheetBinding
    private val viewModel: EpisodeBottomSheetViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = EpisodeBottomSheetBinding.inflate(inflater, container, false)

        binding.itemActions.playButton.setOnClickListener {
            binding.itemActions.playButton.isEnabled = false
            binding.itemActions.playButton.setIconResource(AndroidR.color.transparent)
            binding.itemActions.progressPlay.isVisible = true
            playerViewModel.loadPlayerItems(viewModel.item)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        when (uiState) {
                            is EpisodeBottomSheetViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                            is EpisodeBottomSheetViewModel.UiState.Loading -> bindUiStateLoading()
                            is EpisodeBottomSheetViewModel.UiState.Error -> bindUiStateError(uiState)
                        }
                    }
                }
                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is EpisodeBottomSheetEvent.NavigateBack -> findNavController().navigateUp()
                            is EpisodeBottomSheetEvent.DownloadError -> {} // Download removed
                        }
                    }
                }
                launch {
                    playerViewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerItemsEvent.PlayerItemsReady -> bindPlayerItems(event.items)
                            is PlayerItemsEvent.PlayerItemsError -> bindPlayerItemsError(event.error)
                        }
                    }
                }
            }
        }

        binding.seriesName.setOnClickListener {
            navigateToSeries(viewModel.item.seriesId, viewModel.item.seriesName)
        }

        binding.itemActions.checkButton.setOnClickListener {
            viewModel.togglePlayed()
        }

        binding.itemActions.favoriteButton.setOnClickListener {
            viewModel.toggleFavorite()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.let {
            val sheet = it as BottomSheetDialog
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadEpisode(args.episodeId)
    }

    private fun bindUiStateNormal(uiState: EpisodeBottomSheetViewModel.UiState.Normal) {
        uiState.apply {
            if (episode.playbackPositionTicks > 0) {
                binding.progressBar.layoutParams.width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (episode.playbackPositionTicks.div(episode.runtimeTicks).times(1.26)).toFloat(),
                    context?.resources?.displayMetrics,
                ).toInt()
                binding.progressBar.isVisible = true
            }

            binding.itemActions.playButton.isEnabled = episode.canPlay && episode.sources.isNotEmpty()
            binding.itemActions.checkButton.isEnabled = true
            binding.itemActions.favoriteButton.isEnabled = true

            bindCheckButtonState(episode.played)
            bindFavoriteButtonState(episode.favorite)

            binding.episodeName.text = if (episode.indexNumberEnd == null) {
                getString(
                    CoreR.string.episode_name_extended,
                    episode.parentIndexNumber,
                    episode.indexNumber,
                    episode.name,
                )
            } else {
                getString(
                    CoreR.string.episode_name_extended_with_end,
                    episode.parentIndexNumber,
                    episode.indexNumber,
                    episode.indexNumberEnd,
                    episode.name,
                )
            }

            binding.seriesName.text = episode.seriesName
            binding.overview.text = fromHtml(episode.overview, 0)
            binding.year.text = formatDateTime(episode.premiereDate)
            binding.playtime.text = getString(CoreR.string.runtime_minutes, episode.runtimeTicks.div(600000000))
            episode.communityRating?.also {
                binding.communityRating.text = episode.communityRating.toString()
                binding.communityRating.isVisible = true
            }
            binding.missingIcon.isVisible = false

            // Enhanced image loading with fallback for Next Up
            val imageUrl = episode.images.primary ?: episode.images.backdrop ?: episode.images.showPrimary
            Timber.d("Episode ${episode.name}: primary=${episode.images.primary}, backdrop=${episode.images.backdrop}, showPrimary=${episode.images.showPrimary}")
            if (imageUrl != null) {
                Timber.d("Loading episode image for ${episode.name}")
                binding.episodeImage.load(imageUrl) {
                    crossfade(200)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_gallery)
                    scale(Scale.FIT)
                }
                binding.episodeImage.isVisible = true
            } else {
                Timber.d("No image for ${episode.name}, using placeholder")
                binding.episodeImage.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.episodeImage.isVisible = true
            }
        }
        binding.loadingIndicator.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
    }

    private fun bindUiStateError(uiState: EpisodeBottomSheetViewModel.UiState.Error) {
        binding.loadingIndicator.isVisible = false
        binding.overview.text = uiState.error.message
    }

    private fun bindPlayerItems(items: List<PlayerItem>) {
        navigateToPlayerActivity(items.toTypedArray())
        binding.itemActions.playButton.setIconResource(CoreR.drawable.ic_play)
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun bindCheckButtonState(played: Boolean) {
        when (played) {
            true -> binding.itemActions.checkButton.setIconTintResource(CoreR.color.red)
            false -> binding.itemActions.checkButton.setIconTintColorAttribute(
                MaterialR.attr.colorOnSecondaryContainer,
                requireActivity().theme,
            )
        }
    }

    private fun bindFavoriteButtonState(favorite: Boolean) {
        val favoriteDrawable = when (favorite) {
            true -> CoreR.drawable.ic_heart_filled
            false -> CoreR.drawable.ic_heart
        }
        binding.itemActions.favoriteButton.setIconResource(favoriteDrawable)
        when (favorite) {
            true -> binding.itemActions.favoriteButton.setIconTintResource(CoreR.color.red)
            false -> binding.itemActions.favoriteButton.setIconTintColorAttribute(
                MaterialR.attr.colorOnSecondaryContainer,
                requireActivity().theme,
            )
        }
    }

    private fun bindPlayerItemsError(error: Exception) {
        Timber.e(error.message)
        binding.playerItemsError.isVisible = true
        playButtonNormal()
        binding.playerItemsErrorDetails.setOnClickListener {
            ErrorDialogFragment.newInstance(error).show(parentFragmentManager, ErrorDialogFragment.TAG)
        }
    }

    private fun playButtonNormal() {
        binding.itemActions.playButton.isEnabled = true
        binding.itemActions.playButton.setIconResource(CoreR.drawable.ic_play)
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun navigateToPlayerActivity(
        playerItems: Array<PlayerItem>,
    ) {
        findNavController().safeNavigate(
            EpisodeBottomSheetFragmentDirections.actionEpisodeBottomSheetFragmentToPlayerActivity(
                playerItems,
            ),
        )
    }

    private fun navigateToSeries(id: UUID, name: String) {
        findNavController().safeNavigate(
            EpisodeBottomSheetFragmentDirections.actionEpisodeBottomSheetFragmentToShowFragment(
                itemId = id,
                itemName = name,
            ),
        )
    }

    private fun formatDateTime(datetime: DateTime?): String {
        if (datetime == null) return ""
        val instant = datetime.toInstant(ZoneOffset.UTC)
        val date = Date.from(instant)
        return DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}
package dev.jdtech.jellyfin.fragments

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.R
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.adapters.PersonListAdapter
import dev.jdtech.jellyfin.adapters.ViewItemListAdapter
import dev.jdtech.jellyfin.bindCardItemImage
import dev.jdtech.jellyfin.bindItemBackdropImage
import dev.jdtech.jellyfin.databinding.FragmentShowBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import dev.jdtech.jellyfin.utils.safeNavigate
import dev.jdtech.jellyfin.utils.setIconTintColorAttribute
import dev.jdtech.jellyfin.viewmodels.PlayerItemsEvent
import dev.jdtech.jellyfin.viewmodels.PlayerViewModel
import dev.jdtech.jellyfin.viewmodels.ShowEvent
import dev.jdtech.jellyfin.viewmodels.ShowViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR
import coil.load
import coil.size.Scale

@AndroidEntryPoint
class ShowFragment : Fragment() {
    private lateinit var binding: FragmentShowBinding
    private val viewModel: ShowViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val args: ShowFragmentArgs by navArgs()

    private lateinit var errorDialog: ErrorDialogFragment

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var repository: JellyfinRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentShowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        when (uiState) {
                            is ShowViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                            is ShowViewModel.UiState.Loading -> bindUiStateLoading()
                            is ShowViewModel.UiState.Error -> bindUiStateError(uiState)
                        }
                    }
                }
                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is ShowEvent.NavigateBack -> findNavController().navigateUp()
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

        binding.errorLayout.errorRetryButton.setOnClickListener {
            viewModel.loadData(args.itemId, false)
        }

        binding.itemActions.trailerButton.setOnClickListener {
            viewModel.item.trailer.let { trailerUri ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(trailerUri),
                )
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.nextUp.setOnClickListener {
            navigateToEpisodeBottomSheetFragment(viewModel.nextUp!!)
        }

        binding.seasonsRecyclerView.adapter =
            ViewItemListAdapter(
                { season ->
                    if (season is FindroidSeason) navigateToSeasonFragment(season)
                },
                fixedWidth = true,
            )
        binding.peopleRecyclerView.adapter = PersonListAdapter { person ->
            navigateToPersonDetail(person.id)
        }

        binding.itemActions.playButton.setOnClickListener {
            if (!isNetworkAvailable(requireContext())) {
                Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.itemActions.playButton.isEnabled = false
            binding.itemActions.playButton.setIconResource(android.R.color.transparent)
            binding.itemActions.progressPlay.isVisible = true
            Snackbar.make(binding.root, "Loading video source...", Snackbar.LENGTH_LONG).show()

            // If nextUp exists, use it
            if (viewModel.nextUp != null) {
                playerViewModel.loadPlayerItems(viewModel.nextUp!!)
                return@setOnClickListener
            }

            // Fallback: Load first episode of latest season
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val latestSeason = repository.getSeasons(args.itemId)
                        .maxByOrNull { it.indexNumber ?: 0 }
                    if (latestSeason == null) {
                        throw IllegalStateException("No seasons available")
                    }
                    val firstEpisode = repository.getEpisodes(
                        seriesId = args.itemId,
                        seasonId = latestSeason.id,
                        fields = listOf(ItemFields.MEDIA_SOURCES),
                        limit = 1,
                    ).firstOrNull()
                    if (firstEpisode == null) {
                        throw IllegalStateException("No playable episode found")
                    }
                    // Run on main thread to call loadPlayerItems
                    withContext(Dispatchers.Main) {
                        playerViewModel.loadPlayerItems(firstEpisode)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Timber.e(e, "Failed to load fallback episode: ${e.message}")
                        playButtonNormal()
                        Snackbar.make(
                            binding.root,
                            "Error: ${e.message ?: "No playable episode found"}",
                            Snackbar.LENGTH_LONG
                        ).setAction("Retry") {
                            binding.itemActions.playButton.performClick()
                        }.show()
                    }
                }
            }
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(parentFragmentManager, ErrorDialogFragment.TAG)
        }

        binding.itemActions.checkButton.setOnClickListener {
            viewModel.togglePlayed()
        }

        binding.itemActions.favoriteButton.setOnClickListener {
            viewModel.toggleFavorite()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData(args.itemId, false)
    }

    private fun bindUiStateNormal(uiState: ShowViewModel.UiState.Normal) {
        uiState.apply {
            binding.originalTitle.isVisible = item.originalTitle != item.name
            if (item.trailer != null) {
                binding.itemActions.trailerButton.isVisible = true
            }
            binding.actors.isVisible = actors.isNotEmpty()

            binding.itemActions.playButton.isEnabled = item.canPlay
            binding.itemActions.checkButton.isEnabled = true
            binding.itemActions.favoriteButton.isEnabled = true

            bindCheckButtonState(item.played)
            bindFavoriteButtonState(item.favorite)

            binding.name.text = item.name
            binding.originalTitle.text = item.originalTitle
            if (dateString.isEmpty()) {
                binding.year.isVisible = false
            } else {
                binding.year.text = dateString
            }
            if (runTime.isEmpty()) {
                binding.playtime.isVisible = false
            } else {
                binding.playtime.text = runTime
            }
            binding.officialRating.text = item.officialRating
            item.communityRating?.also {
                binding.communityRating.text = item.communityRating.toString()
                binding.communityRating.isVisible = true
            }

            binding.info.description.text = fromHtml(item.overview, 0)
            binding.info.genres.text = genresString
            binding.info.genresGroup.isVisible = item.genres.isNotEmpty()
            binding.info.director.text = director?.name
            binding.info.directorGroup.isVisible = director != null
            binding.info.writers.text = writersString
            binding.info.writersGroup.isVisible = writers.isNotEmpty()

            binding.nextUpLayout.isVisible = nextUp != null
            if (nextUp?.indexNumberEnd == null) {
                binding.nextUpName.text = getString(
                    CoreR.string.episode_name_extended,
                    nextUp?.parentIndexNumber,
                    nextUp?.indexNumber,
                    nextUp?.name,
                )
            } else {
                binding.nextUpName.text = getString(
                    CoreR.string.episode_name_extended_with_end,
                    nextUp?.parentIndexNumber,
                    nextUp?.indexNumber,
                    nextUp?.indexNumberEnd,
                    nextUp?.name,
                )
            }

            binding.seasonsLayout.isVisible = seasons.isNotEmpty()
            val seasonsAdapter = binding.seasonsRecyclerView.adapter as ViewItemListAdapter
            seasonsAdapter.submitList(seasons)
            val actorsAdapter = binding.peopleRecyclerView.adapter as PersonListAdapter
            actorsAdapter.submitList(actors)
            bindItemBackdropImage(binding.itemBanner, item)
            // Fallback to primary image if backdrop is not available
            if (item.images.backdrop == null) {
                item.images.primary?.let { primaryUri ->
                    binding.itemBanner.load(primaryUri) {
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.stat_notify_error)
                        scale(Scale.FIT)
                    }
                    binding.itemBanner.isVisible = true
                } ?: run {
                    binding.itemBanner.isVisible = false
                }
            }
            if (nextUp != null) bindCardItemImage(binding.nextUpImage, nextUp!!)
        }
        binding.loadingIndicator.isVisible = false
        binding.mediaInfoScrollview.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateError(uiState: ShowViewModel.UiState.Error) {
        errorDialog = ErrorDialogFragment.newInstance(uiState.error)
        binding.loadingIndicator.isVisible = false
        binding.mediaInfoScrollview.isVisible = false
        binding.errorLayout.errorPanel.isVisible = true
        checkIfLoginRequired(uiState.error.message)
    }

    private fun bindCheckButtonState(played: Boolean) {
        when (played) {
            true -> binding.itemActions.checkButton.setIconTintResource(CoreR.color.red)
            false -> binding.itemActions.checkButton.setIconTintColorAttribute(
                R.attr.colorOnSecondaryContainer,
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
                R.attr.colorOnSecondaryContainer,
                requireActivity().theme,
            )
        }
    }

    private fun bindPlayerItems(items: List<PlayerItem>) {
        navigateToPlayerActivity(items.toTypedArray())
        playButtonNormal()
    }

    private fun bindPlayerItemsError(error: Exception) {
        Timber.e(error, "Player items error: ${error.message}")
        playButtonNormal()
        val errorMessage = when (error) {
            is java.io.IOException -> "Network error: Please check your connection."
            is IllegalStateException -> "No video source available. Try again later."
            else -> "Error loading video: ${error.message}"
        }
        Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                if (isNetworkAvailable(requireContext())) {
                    binding.itemActions.playButton.performClick()
                } else {
                    Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
        binding.playerItemsErrorDetails.setOnClickListener {
            errorDialog = ErrorDialogFragment.newInstance(error)
            errorDialog.show(parentFragmentManager, ErrorDialogFragment.TAG)
        }
    }

    private fun playButtonNormal() {
        binding.itemActions.playButton.isEnabled = true
        binding.itemActions.playButton.setIconResource(CoreR.drawable.ic_play)
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun navigateToEpisodeBottomSheetFragment(episode: FindroidItem) {
        findNavController().safeNavigate(
            ShowFragmentDirections.actionShowFragmentToEpisodeBottomSheetFragment(
                episode.id,
            ),
        )
    }

    private fun navigateToSeasonFragment(season: FindroidSeason) {
        findNavController().safeNavigate(
            ShowFragmentDirections.actionShowFragmentToSeasonFragment(
                season.seriesId,
                season.id,
                season.seriesName,
                season.name,
                false,
            ),
        )
    }

    private fun navigateToPlayerActivity(
        playerItems: Array<PlayerItem>,
    ) {
        findNavController().safeNavigate(
            ShowFragmentDirections.actionShowFragmentToPlayerActivity(
                playerItems,
            ),
        )
    }

    private fun navigateToPersonDetail(personId: UUID) {
        findNavController().safeNavigate(
            ShowFragmentDirections.actionShowFragmentToPersonDetailFragment(personId),
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
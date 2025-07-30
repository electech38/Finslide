// app/phone/src/main/java/dev/jdtech/jellyfin/fragments/MovieFragment.kt
// 🔧 REMOVED TMDB & LOADING STATE - Sạch sẽ, chỉ Jellyfin trailer
package dev.jdtech.jellyfin.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html.fromHtml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import coil.size.Scale
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.adapters.PersonListAdapter
import dev.jdtech.jellyfin.bindItemBackdropImage
import dev.jdtech.jellyfin.databinding.FragmentMovieBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.dialogs.getVideoVersionDialog
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.DisplayProfile
import dev.jdtech.jellyfin.models.AudioCodec
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import dev.jdtech.jellyfin.utils.safeNavigate
import dev.jdtech.jellyfin.utils.setIconTintColorAttribute
import dev.jdtech.jellyfin.viewmodels.MovieEvent
import dev.jdtech.jellyfin.viewmodels.MovieViewModel
import dev.jdtech.jellyfin.viewmodels.PlayerItemsEvent
import dev.jdtech.jellyfin.viewmodels.PlayerViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class MovieFragment : Fragment() {
    private lateinit var binding: FragmentMovieBinding
    private val viewModel: MovieViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val args: MovieFragmentArgs by navArgs()

    private lateinit var errorDialog: ErrorDialogFragment
    private var isTrailerPlaying = false
    private var isMuted = true

    @Inject
    lateinit var appPreferences: AppPreferences

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val POSTER_DISPLAY_MS = 1000L // Hiển thị poster 2 giây
        private const val TRAILER_HIDE_MS = 2000L // Ẩn trailer 2 giây trước khi phát
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentMovieBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWebView()
        setupClickListeners()
        observeViewModels()

        binding.peopleRecyclerView.adapter = PersonListAdapter { person ->
            navigateToPersonDetail(person.id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.trailerWebview?.apply {
            settings?.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.d("WebView page finished loading: $url")
                }
            }

            webChromeClient = WebChromeClient()

            // Add JavaScript interface for communication
            addJavascriptInterface(TrailerInterface(), "AndroidInterface")
        }
    }

    private fun setupClickListeners() {
        binding.errorLayout.errorRetryButton.setOnClickListener {
            viewModel.loadData(args.itemId)
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(parentFragmentManager, ErrorDialogFragment.TAG)
        }

        binding.itemActions.playButton.setOnClickListener {
            binding.itemActions.playButton.isEnabled = false
            binding.itemActions.playButton.setIconResource(android.R.color.transparent)
            binding.itemActions.progressPlay.isVisible = true
            if (viewModel.item.sources.filter { it.type == FindroidSourceType.REMOTE }.size > 1) {
                val dialog = getVideoVersionDialog(
                    requireContext(),
                    viewModel.item,
                    onItemSelected = {
                        playerViewModel.loadPlayerItems(viewModel.item, it)
                    },
                    onCancel = {
                        playButtonNormal()
                    },
                )
                dialog.show()
                return@setOnClickListener
            }
            playerViewModel.loadPlayerItems(viewModel.item)
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

        binding.itemActions.checkButton.setOnClickListener {
            viewModel.togglePlayed()
        }

        binding.itemActions.favoriteButton.setOnClickListener {
            viewModel.toggleFavorite()
        }

        // Speaker button for trailer sound control
        binding.speakerButton?.setOnClickListener {
            toggleTrailerSound()
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        when (uiState) {
                            is MovieViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                            is MovieViewModel.UiState.Loading -> bindUiStateLoading()
                            is MovieViewModel.UiState.Error -> bindUiStateError(uiState)
                        }
                    }
                }
                launch {
                    viewModel.trailerState.collect { trailerState ->
                        handleTrailerState(trailerState)
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
                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is MovieEvent.NavigateBack -> findNavController().navigateUp()
                            is MovieEvent.DownloadError -> createErrorDialog(event.uiText)
                        }
                    }
                }
            }
        }
    }

    private fun handleTrailerState(trailerState: MovieViewModel.TrailerState) {
        when (trailerState) {
            is MovieViewModel.TrailerState.Ready -> {
                loadTrailerInWebView(trailerState.embedUrl)
                Timber.d("🎬 Trailer ready: ${trailerState.embedUrl}")
            }
            is MovieViewModel.TrailerState.Error -> {
                Timber.e(trailerState.error, "🎬 Trailer loading error")
                // Keep showing poster
            }
            is MovieViewModel.TrailerState.None -> {
                Timber.d("🎬 No trailer available")
                // Keep showing poster
            }
        }
    }

    private fun loadTrailerInWebView(embedUrl: String) {
        try {
            val videoId = extractVideoIdFromUrl(embedUrl)
            if (videoId != null) {
                val htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; padding: 0; background-color: black; overflow: hidden; }
                            #player { width: 100%; height: 100vh; border: none; }
                        </style>
                    </head>
                    <body>
                        <div id="player"></div>
                        <script>
                            let player;
                            let isMuted = true;
                            
                            function onYouTubeIframeAPIReady() {
                                initializePlayer('$videoId');
                            }
                            
                            function initializePlayer(videoId) {
                                player = new YT.Player('player', {
                                    height: '100%',
                                    width: '100%',
                                    videoId: videoId,
                                    playerVars: {
                                        autoplay: 1, mute: 1, controls: 0, modestbranding: 1, rel: 0,
                                        showinfo: 0, iv_load_policy: 3, fs: 0, disablekb: 1, playsinline: 1
                                    },
                                    events: {
                                        onReady: function(event) {
                                            event.target.playVideo();
                                            AndroidInterface.onTrailerReady();
                                        },
                                        onStateChange: function(event) {
                                            if (event.data === YT.PlayerState.ENDED) {
                                                AndroidInterface.onTrailerEnded();
                                            }
                                        },
                                        onError: function(event) {
                                            AndroidInterface.onTrailerError('Player error: ' + event.data);
                                        }
                                    }
                                });
                            }
                            
                            function toggleMute() {
                                if (player) {
                                    if (isMuted) {
                                        player.unMute();
                                        isMuted = false;
                                        AndroidInterface.onSoundToggled(false);
                                    } else {
                                        player.mute();
                                        isMuted = true;
                                        AndroidInterface.onSoundToggled(true);
                                    }
                                }
                            }
                        </script>
                        <script src="https://www.youtube.com/iframe_api"></script>
                    </body>
                    </html>
                """.trimIndent()

                binding.trailerWebview?.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading trailer in WebView")
        }
    }

    private fun extractVideoIdFromUrl(url: String): String? {
        val regex = Regex("(?:youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/)([^\"&?/\\s]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun showTrailer() {
        isTrailerPlaying = true
        binding.itemBanner.isVisible = false
        binding.trailerWebview?.isVisible = true
        binding.speakerButton?.isVisible = true
    }

    private fun hideTrailer() {
        isTrailerPlaying = false
        binding.itemBanner.isVisible = true
        binding.trailerWebview?.isVisible = false
        binding.speakerButton?.isVisible = false
    }

    private fun toggleTrailerSound() {
        binding.trailerWebview?.evaluateJavascript("toggleMute();", null)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData(args.itemId)
    }

    override fun onPause() {
        super.onPause()
        if (isTrailerPlaying) {
            binding.trailerWebview?.evaluateJavascript("pauseVideo();", null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.trailerWebview?.destroy()
        handler.removeCallbacksAndMessages(null) // Dọn dẹp Handler khi Fragment bị hủy
    }

    // JavaScript Interface for communication between WebView and Android
    inner class TrailerInterface {
        @JavascriptInterface
        fun onTrailerReady() {
            requireActivity().runOnUiThread {
                Timber.d("🎬 Trailer ready to play")
                // Sau 5 giây hiển thị poster, ẩn trailer
                handler.postDelayed({
                    if (isAdded && !isDetached) {
                        hideTrailer()
                        // Sau 2 giây ẩn trailer, hiển thị và phát trailer
                        handler.postDelayed({
                            if (isAdded && !isDetached) {
                                showTrailer()
                            }
                        }, TRAILER_HIDE_MS)
                    }
                }, POSTER_DISPLAY_MS)
            }
        }

        @JavascriptInterface
        fun onTrailerEnded() {
            requireActivity().runOnUiThread {
                Timber.d("🎬 Trailer ended")
                hideTrailer()
            }
        }

        @JavascriptInterface
        fun onTrailerError(error: String) {
            requireActivity().runOnUiThread {
                Timber.e("🎬 Trailer error: $error")
                hideTrailer()
            }
        }

        @JavascriptInterface
        fun onSoundToggled(muted: Boolean) {
            requireActivity().runOnUiThread {
                isMuted = muted
                binding.speakerButton?.setImageResource(
                    if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
                )
            }
        }

        @JavascriptInterface
        fun onTrailerPlaying() {
            requireActivity().runOnUiThread {
                Timber.d("🎬 Trailer playing")
            }
        }

        @JavascriptInterface
        fun onTrailerPaused() {
            requireActivity().runOnUiThread {
                Timber.d("🎬 Trailer paused")
            }
        }
    }

    // Rest of the existing methods remain the same...
    private fun bindUiStateNormal(uiState: MovieViewModel.UiState.Normal) {
        uiState.apply {
            binding.originalTitle.isVisible = item.originalTitle != item.name
            if (item.trailer != null) {
                binding.itemActions.trailerButton.isVisible = true
            }
            binding.actors.isVisible = actors.isNotEmpty()

            binding.itemActions.playButton.isEnabled = item.canPlay && item.sources.isNotEmpty()
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
                binding.communityRating.text = it.toString()
                binding.communityRating.isVisible = true
            }

            videoMetadata.let {
                with(binding) {
                    videoMetaChips.isVisible = true
                    audioChannelChip.text = it.audioChannels.firstOrNull()?.raw
                    resChip.text = it.resolution.firstOrNull()?.raw
                    audioChannelChip.isVisible = it.audioChannels.isNotEmpty()
                    resChip.isVisible = it.resolution.isNotEmpty()

                    it.displayProfiles.firstOrNull()?.apply {
                        videoProfileChip.text = this.raw
                        videoProfileChip.isVisible = when (this) {
                            DisplayProfile.HDR10,
                            DisplayProfile.HDR10_PLUS,
                            DisplayProfile.HLG -> {
                                videoProfileChip.chipStartPadding = .0f
                                true
                            }
                            DisplayProfile.DOLBY_VISION -> {
                                videoProfileChip.isChipIconVisible = true
                                true
                            }
                            else -> false
                        }
                    }

                    audioCodecChip.text = when (val codec = it.audioCodecs.firstOrNull()) {
                        AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.TRUEHD -> {
                            audioCodecChip.isVisible = true
                            if (it.isAtmos.firstOrNull() == true) {
                                "${codec.raw} | Atmos"
                            } else {
                                codec.raw
                            }
                        }
                        AudioCodec.DTS -> {
                            audioCodecChip.apply {
                                isVisible = true
                                isChipIconVisible = false
                                chipStartPadding = .0f
                            }
                            codec.raw
                        }
                        else -> {
                            audioCodecChip.isVisible = false
                            null
                        }
                    }
                }
            }

            binding.subsChip.isVisible = subtitleString.isNotEmpty()

            if (appPreferences.displayExtraInfo) {
                binding.info.video.text = videoString
                binding.info.videoGroup.isVisible = videoString.isNotEmpty()
                binding.info.audio.text = audioString
                binding.info.audioGroup.isVisible = audioString.isNotEmpty()
                binding.info.subtitles.text = subtitleString
                binding.info.subtitlesGroup.isVisible = subtitleString.isNotEmpty()
            }

            binding.info.description.text = fromHtml(item.overview, 0)
            binding.info.genres.text = genresString
            binding.info.genresGroup.isVisible = item.genres.isNotEmpty()
            binding.info.director.text = director?.name
            binding.info.directorGroup.isVisible = director != null
            binding.info.writers.text = writersString
            binding.info.writersGroup.isVisible = writers.isNotEmpty()

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

            // Load logo using Coil
            item.images.logo?.let { logoUri ->
                binding.logoImage.load(logoUri) {
                    crossfade(300)
                }
                binding.logoImage.isVisible = true
            } ?: run {
                binding.logoImage.isVisible = false
            }
        }
        binding.loadingIndicator.isVisible = false
        binding.mediaInfoScrollview.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateError(uiState: MovieViewModel.UiState.Error) {
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
                com.google.android.material.R.attr.colorOnSecondaryContainer,
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
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                requireActivity().theme,
            )
        }
    }

    private fun bindPlayerItems(items: List<PlayerItem>) {
        navigateToPlayerActivity(items.toTypedArray())
        binding.itemActions.playButton.setIconResource(CoreR.drawable.ic_play)
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun bindPlayerItemsError(error: Exception) {
        Timber.e(error.message)
        binding.playerItemsError.visibility = View.VISIBLE
        playButtonNormal()
        binding.playerItemsErrorDetails.setOnClickListener {
            ErrorDialogFragment.newInstance(error)
                .show(parentFragmentManager, ErrorDialogFragment.TAG)
        }
    }

    private fun playButtonNormal() {
        binding.itemActions.playButton.isEnabled = true
        binding.itemActions.playButton.setIconResource(CoreR.drawable.ic_play)
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun createErrorDialog(uiText: UiText) {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        builder
            .setTitle(CoreR.string.downloading_error)
            .setMessage(uiText.asString(requireContext().resources))
            .setPositiveButton(getString(CoreR.string.close)) { _, _ -> }
        builder.show()
    }

    private fun navigateToPlayerActivity(playerItems: Array<PlayerItem>) {
        findNavController().safeNavigate(
            MovieFragmentDirections.actionMovieFragmentToPlayerActivity(playerItems),
        )
    }

    private fun navigateToPersonDetail(personId: UUID) {
        findNavController().safeNavigate(
            MovieFragmentDirections.actionMovieFragmentToPersonDetailFragment(personId),
        )
    }
}
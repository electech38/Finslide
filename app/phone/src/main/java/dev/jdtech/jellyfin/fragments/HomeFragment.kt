package dev.jdtech.jellyfin.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.adapters.ViewListAdapter
import dev.jdtech.jellyfin.adapters.FeaturedBannerAdapter
import dev.jdtech.jellyfin.databinding.FragmentHomeBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.dialogs.SearchDialogFragment
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import dev.jdtech.jellyfin.utils.restart
import dev.jdtech.jellyfin.utils.safeNavigate
import dev.jdtech.jellyfin.viewmodels.HomeViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import coil.load
import coil.request.CachePolicy
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.ErrorResult
import coil.size.Scale
import android.util.Log
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val viewModel: HomeViewModel by viewModels()

    private var originalSoftInputMode: Int? = null
    private lateinit var errorDialog: ErrorDialogFragment

    // Featured banner properties
    private lateinit var featuredBannerAdapter: FeaturedBannerAdapter
    private val autoSlideHandler = Handler(Looper.getMainLooper())
    private var autoSlideRunnable: Runnable? = null
    private val autoSlideDelay = 10000L // 10 seconds
    private var lastFeaturedItems: List<FindroidItem> = emptyList()
    private var isRefreshing = false
    private var isDataLoaded = false
    private var recyclerViewPosition = 0
    private var lastRefreshTime = 0L
    private val REFRESH_INTERVAL = 60 * 1000L // 1 minute

    @Inject
    lateinit var appPreferences: AppPreferences

    // ========== RELAXED FILTERING CONSTANTS ==========
    companion object {
        private const val FEATURED_ITEMS_LIMIT = 50 // Increased limit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("HomeFragment onCreate")
        
        isDataLoaded = savedInstanceState?.getBoolean("isDataLoaded", false) ?: false
        recyclerViewPosition = savedInstanceState?.getInt("recyclerViewPosition", 0) ?: 0
        lastRefreshTime = savedInstanceState?.getLong("lastRefreshTime", 0L) ?: 0L
        
        val preferences = activity?.getSharedPreferences("home_fragment", Context.MODE_PRIVATE)
        val savedPosition = preferences?.getInt("scroll_position", 0) ?: 0
        if (savedPosition > 0) {
            recyclerViewPosition = savedPosition
        }
        
        Timber.d("onCreate, isDataLoaded=$isDataLoaded, recyclerViewPosition=$recyclerViewPosition")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Timber.d("HomeFragment onCreateView")
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupView()
        setupFeaturedBanner()
        bindState()
        
        if (!isDataLoaded) {
            Timber.d("Loading data for HomeFragment")
            viewModel.loadData()
            isDataLoaded = true
            lastRefreshTime = System.currentTimeMillis()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSettings.setOnClickListener {
            navigateToSettingsFragment()
        }

        binding.btnSearch.setOnClickListener {
            showSearchDialog()
        }
    }

    private fun showSearchDialog() {
        val searchDialog = SearchDialogFragment { query ->
            if (!query.isNullOrEmpty()) {
                navigateToSearchResultFragment(query)
            }
        }
        searchDialog.show(parentFragmentManager, "SearchDialog")
    }

    override fun onStart() {
        super.onStart()
        requireActivity().window.let {
            originalSoftInputMode = it.attributes?.softInputMode
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("HomeFragment onResume")
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime > REFRESH_INTERVAL) {
            Timber.d("Refresh interval exceeded, loading data")
            viewModel.loadData(forceRefresh = false)
            lastRefreshTime = currentTime
        } else {
            Timber.d("Within refresh interval, skipping data load")
            if (binding.viewsRecyclerView.adapter?.itemCount ?: 0 > 0) {
                restoreScrollPosition()
            }
        }
        
        binding.root.postDelayed({
            startAutoSlide()
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        stopAutoSlide()
        saveScrollPosition()
    }

    private fun saveScrollPosition() {
        if (binding.viewsRecyclerView.layoutManager is LinearLayoutManager) {
            val position = (binding.viewsRecyclerView.layoutManager as LinearLayoutManager)
                .findFirstVisibleItemPosition()
            
            val preferences = activity?.getSharedPreferences("home_fragment", Context.MODE_PRIVATE)
            preferences?.edit()?.apply {
                putInt("scroll_position", position)
                apply()
            }
            
            recyclerViewPosition = position
            Timber.d("Saved scroll position: $position to SharedPreferences")
        }
    }

    private fun restoreScrollPosition() {
        val preferences = activity?.getSharedPreferences("home_fragment", Context.MODE_PRIVATE)
        val savedPosition = preferences?.getInt("scroll_position", 0) ?: recyclerViewPosition
        
        if (savedPosition > 0) {
            binding.viewsRecyclerView.postDelayed({
                try {
                    if (binding.viewsRecyclerView.adapter?.itemCount ?: 0 > savedPosition) {
                        val layoutManager = binding.viewsRecyclerView.layoutManager as? LinearLayoutManager
                        layoutManager?.scrollToPositionWithOffset(savedPosition, 0)
                        Timber.d("Restored scroll position from SharedPreferences: $savedPosition")
                    } else {
                        Timber.d("Cannot restore position $savedPosition - not enough items (${binding.viewsRecyclerView.adapter?.itemCount ?: 0})")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to restore scroll position")
                }
            }, 300)
        }
    }

    override fun onStop() {
        super.onStop()
        originalSoftInputMode?.let { activity?.window?.setSoftInputMode(it) }
        stopAutoSlide()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isDataLoaded", isDataLoaded)
        outState.putInt("recyclerViewPosition", recyclerViewPosition)
        outState.putLong("lastRefreshTime", lastRefreshTime)
    }

    private fun setupView() {
        binding.refreshLayout.setOnRefreshListener {
            Timber.d("Refresh triggered")
            lastFeaturedItems = emptyList()
            isRefreshing = true
            lastRefreshTime = System.currentTimeMillis()
            stopAutoSlide()
            viewModel.loadData(forceRefresh = true)
        }
        binding.refreshLayout.isRefreshing = false

        val viewListAdapter = ViewListAdapter(
            onClickListener = { navigateToLibraryFragment(it) },
            onItemClickListener = { navigateToMediaItem(it) },
            onOnlineClickListener = {
                appPreferences.offlineMode = false
                activity?.restart()
            },
        )
        binding.viewsRecyclerView.adapter = viewListAdapter

        if (binding.viewsRecyclerView.layoutManager == null) {
            binding.viewsRecyclerView.layoutManager = LinearLayoutManager(context)
        }

        binding.viewsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isRefreshing &&
                        (visibleItemCount + firstVisibleItemPosition + 5 >= totalItemCount) &&
                        firstVisibleItemPosition >= 0
                    ) {
                        Timber.d("Scrolled near the end, loading more sections")
                    }
                }
            }
        })

        binding.errorLayout.errorRetryButton.setOnClickListener {
            stopAutoSlide()
            viewModel.loadData()
            lastRefreshTime = System.currentTimeMillis()
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(parentFragmentManager, ErrorDialogFragment.TAG)
        }
    }

    private fun setupFeaturedBanner() {
        Timber.d("Setting up featured banner")
        featuredBannerAdapter = FeaturedBannerAdapter { item ->
            navigateToMediaItem(item)
        }
        binding.featuredViewpager.adapter = featuredBannerAdapter
        binding.featuredViewpager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        binding.btnFeaturedPrev.setOnClickListener {
            val currentPosition = binding.featuredViewpager.currentItem
            val itemCount = featuredBannerAdapter.itemCount
            if (itemCount > 0) {
                val prevPosition = if (currentPosition == 0) itemCount - 1 else currentPosition - 1
                binding.featuredViewpager.setCurrentItem(prevPosition, true)
            }
        }

        binding.btnFeaturedNext.setOnClickListener {
            val currentPosition = binding.featuredViewpager.currentItem
            val itemCount = featuredBannerAdapter.itemCount
            if (itemCount > 0) {
                val nextPosition = (currentPosition + 1) % itemCount
                binding.featuredViewpager.setCurrentItem(nextPosition, true)
            }
        }

        binding.featuredViewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateFeaturedInfo(position)
                updateNavigationButtons(position)
                restartAutoSlide()
                applyZoomEffect()
            }
        })
    }

    private fun applyZoomEffect() {
        val currentPage = binding.featuredViewpager.currentItem
        val viewPager = binding.featuredViewpager
        val currentView = viewPager.getChildAt(0)
        if (currentView != null) {
            currentView.scaleX = 1.0f
            currentView.scaleY = 1.0f
            currentView.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(4000)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun bindState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    Timber.d("UiState received: $uiState")
                    when (uiState) {
                        is HomeViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                        is HomeViewModel.UiState.Loading -> bindUiStateLoading()
                        is HomeViewModel.UiState.Error -> bindUiStateError(uiState)
                    }
                }
            }
        }
    }

    private fun bindUiStateNormal(uiState: HomeViewModel.UiState.Normal) {
        Timber.d("Binding normal state with ${uiState.homeItems.size} home items")
        uiState.apply {
            val adapter = binding.viewsRecyclerView.adapter as ViewListAdapter
            adapter.submitList(uiState.homeItems)
            
            if (!isRefreshing) {
                binding.viewsRecyclerView.post {
                    restoreScrollPosition()
                }
            }
            
            stopAutoSlide()
            setupFeaturedItems(uiState)
        }
        binding.loadingIndicator.isVisible = false
        binding.refreshLayout.isRefreshing = false
        binding.viewsRecyclerView.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
        isRefreshing = false
    }

    private fun bindUiStateLoading() {
        Timber.d("Binding loading state")
        if (!isRefreshing) {
            binding.loadingIndicator.isVisible = true
            binding.errorLayout.errorPanel.isVisible = false
            binding.viewsRecyclerView.isVisible = false
        }
    }

    private fun bindUiStateError(uiState: HomeViewModel.UiState.Error) {
        Timber.d("Binding error state: ${uiState.error.message}")
        errorDialog = ErrorDialogFragment.newInstance(uiState.error)
        binding.loadingIndicator.isVisible = false
        binding.refreshLayout.isRefreshing = false
        binding.viewsRecyclerView.isVisible = false
        binding.errorLayout.errorPanel.isVisible = true
        binding.featuredBannerContainer.isVisible = false
        checkIfLoginRequired(uiState.error.message)
        isRefreshing = false
    }

    // ========== SIMPLE FEATURED ITEMS SETUP ==========
    
    /**
     * SIMPLE SETUP FEATURED ITEMS - Just backdrop OR logo required
     */
    private fun setupFeaturedItems(uiState: HomeViewModel.UiState.Normal) {
        Timber.d("Starting setupFeaturedItems")
        
        val featuredItems = mutableListOf<FindroidItem>()
        uiState.homeItems.forEach { homeItem ->
            when (homeItem) {
                is HomeItem.ViewItem -> {
                    homeItem.view.items?.let { items ->
                        featuredItems.addAll(items)
                        Timber.d("Added ${items.size} items from ViewItem: ${homeItem.view.name}")
                    }
                }
                is HomeItem.Section -> {
                    homeItem.homeSection.items?.let { items ->
                        featuredItems.addAll(items)
                        Timber.d("Added ${items.size} items from Section")
                    }
                }
                else -> {}
            }
        }
        
        Timber.d("Total featured candidates: ${featuredItems.size}")
        
        // SIMPLE FILTERING - Only backdrop OR logo required
        val filteredItems = featuredItems
            .asSequence()
            .filter { it is FindroidMovie || it is FindroidShow }
            .also { 
                val count = it.count()
                Timber.d("After type filter (Movie/Show): $count")
            }
            .filter { hasValidImageBasic(it) } // Only check for backdrop OR logo
            .also { 
                val count = it.count()
                Timber.d("After image filter: $count")
            }
            .distinctBy { it.id }
            .also { 
                val count = it.count()
                Timber.d("After distinct filter: $count")
            }
            .take(FEATURED_ITEMS_LIMIT)
            .toList()
        
        Timber.i("Featured Banner: Selected ${filteredItems.size} items from ${featuredItems.size} candidates")
        
        if (filteredItems.isNotEmpty()) {
            // Show banner
            binding.featuredBannerContainer.isVisible = true
            featuredBannerAdapter.submitList(filteredItems)
            
            binding.featuredViewpager.post {
                binding.featuredViewpager.setCurrentItem(0, false)
                updateFeaturedInfo(0)
                updateNavigationButtons(0)
                applyZoomEffect()
            }
            
            binding.root.postDelayed({
                startAutoSlide()
            }, 500)
            
            Timber.i("Featured Banner displayed successfully with ${filteredItems.size} items")
        } else {
            // Hide banner
            binding.featuredBannerContainer.isVisible = false
            Timber.w("Featured Banner hidden - no items available")
        }
        
        lastFeaturedItems = filteredItems
    }

    // ========== SIMPLE FILTERING FUNCTIONS ==========
    
    /**
     * SIMPLE VALIDATION - Only check backdrop OR logo (not both)
     */
    private fun hasValidImageBasic(item: FindroidItem): Boolean {
        return when (item) {
            is FindroidMovie, is FindroidShow -> {
                val hasBackdrop = item.images.backdrop != null
                val hasLogo = item.images.logo != null
                val result = hasBackdrop || hasLogo // Just need ONE of them
                
                if (!result) {
                    Timber.d("Rejected ${item.name}: No backdrop or logo")
                }
                result
            }
            else -> false
        }
    }

    // ========== UPDATE FEATURED INFO ==========
    
    /**
     * Simple updateFeaturedInfo with safe element access
     */
    private fun updateFeaturedInfo(position: Int) {
        val items = featuredBannerAdapter.currentList
        Timber.d("Updating featured info for position $position, total items: ${items.size}")
        
        if (position < items.size) {
            val item = items[position]
            Timber.d("Featured item: ${item.name}")

            // Safe reset of elements
            try {
                binding.featuredLogo.setImageDrawable(null)
                binding.featuredLogo.isVisible = false
                
                // Reset rating containers if they exist
                binding.featuredImdbContainer?.isVisible = false
                binding.featuredTmdbContainer?.isVisible = false
                binding.featuredCriticContainer?.isVisible = false
            } catch (e: Exception) {
                Timber.e(e, "Error resetting UI elements")
            }

            // Load logo if available
            item.images.logo?.let { logoUri ->
                binding.featuredLogo.alpha = 0f
                binding.featuredLogo.isVisible = true
                binding.featuredLogo.load(logoUri) {
                    crossfade(300)
                    scale(Scale.FIT)
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    listener(
                        onSuccess = { _, _ ->
                            binding.featuredLogo.animate()
                                .alpha(1f)
                                .setDuration(300L)
                                .start()
                        },
                        onError = { _, errorResult: ErrorResult ->
                            Timber.log(Log.WARN, errorResult.throwable, "Failed to load logo: $logoUri")
                            binding.featuredLogo.isVisible = false
                        }
                    )
                }
            }

            // Setup ratings and info
            when (item) {
                is FindroidMovie -> {
                    setupMovieRatings(item)
                    setupMovieInfo(item)
                }
                is FindroidShow -> {
                    setupShowRatings(item)
                    setupShowInfo(item)
                }
            }

            // Preload next image
            preloadNextFeaturedImage(position, items)
        }
    }

    /**
     * Simple setup ratings for movies
     */
    private fun setupMovieRatings(movie: FindroidMovie) {
        try {
            // IMDb Rating (Community Rating) - Only show if exists
            movie.communityRating?.let { rating ->
                binding.featuredImdbRating?.text = String.format("%.1f", rating)
                binding.featuredImdbContainer?.isVisible = true
                
                // Also setup TMDB percentage (community rating * 10)
                val tmdbRating = (rating * 10).toInt()
                binding.featuredTmdbRating?.text = "$tmdbRating%"
                binding.featuredTmdbContainer?.isVisible = true
                
                Timber.d("Movie ratings - IMDb: $rating, TMDB: $tmdbRating%")
            } ?: run {
                binding.featuredImdbContainer?.isVisible = false
                binding.featuredTmdbContainer?.isVisible = false
            }
            
            // Hide critic rating (not available in FindroidMovie)
            binding.featuredCriticContainer?.isVisible = false
            
        } catch (e: Exception) {
            Timber.e(e, "Error in setupMovieRatings")
        }
    }

    /**
     * Simple setup ratings for TV shows
     */
    private fun setupShowRatings(show: FindroidShow) {
        try {
            // IMDb Rating (Community Rating) - Only show if exists
            show.communityRating?.let { rating ->
                binding.featuredImdbRating?.text = String.format("%.1f", rating)
                binding.featuredImdbContainer?.isVisible = true
                
                // Also setup TMDB percentage (community rating * 10)
                val tmdbRating = (rating * 10).toInt()
                binding.featuredTmdbRating?.text = "$tmdbRating%"
                binding.featuredTmdbContainer?.isVisible = true
                
                Timber.d("Show ratings - IMDb: $rating, TMDB: $tmdbRating%")
            } ?: run {
                binding.featuredImdbContainer?.isVisible = false
                binding.featuredTmdbContainer?.isVisible = false
            }
            
            // Hide critic rating (not available in FindroidShow)
            binding.featuredCriticContainer?.isVisible = false
            
        } catch (e: Exception) {
            Timber.e(e, "Error in setupShowRatings")
        }
    }

    /**
     * Simple setup movie info
     */
    private fun setupMovieInfo(movie: FindroidMovie) {
        try {
            // Age Rating
            binding.featuredAgeRating?.text = movie.officialRating ?: "NR"
            
            // Year
            binding.featuredYear?.text = movie.productionYear?.toString() ?: "N/A"
            
            // Runtime  
            val runtime = if (movie.runtimeTicks > 0) {
                val totalMinutes = movie.runtimeTicks / 600000000
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                "${hours}h ${minutes}m"
            } else {
                "N/A"
            }
            binding.featuredRuntime?.text = runtime
            
            // Genres
            val genresText = movie.genres.joinToString(", ").takeIf { it.isNotBlank() } ?: "Movie"
            binding.featuredGenres?.text = genresText
            binding.featuredGenres?.isVisible = true
            
        } catch (e: Exception) {
            Timber.e(e, "Error setting movie info")
        }
    }

    /**
     * Simple setup show info
     */
    private fun setupShowInfo(show: FindroidShow) {
        try {
            // Age Rating
            binding.featuredAgeRating?.text = show.officialRating ?: "NR"
            
            // Year
            binding.featuredYear?.text = show.productionYear?.toString() ?: "N/A"
            
            // For shows, show status instead of runtime
            val status = when {
                show.status.equals("Continuing", ignoreCase = true) -> "Ongoing"
                show.status.equals("Ended", ignoreCase = true) -> "Ended"
                else -> show.status ?: "Unknown"
            }
            binding.featuredRuntime?.text = status
            
            // Genres
            val genresText = show.genres.joinToString(", ").takeIf { it.isNotBlank() } ?: "TV Show"
            binding.featuredGenres?.text = genresText
            binding.featuredGenres?.isVisible = true
            
        } catch (e: Exception) {
            Timber.e(e, "Error setting show info")
        }
    }

    /**
     * Preload next featured image for smooth transitions
     */
    private fun preloadNextFeaturedImage(currentPosition: Int, items: List<FindroidItem>) {
        val nextPosition = (currentPosition + 1) % items.size
        if (nextPosition != currentPosition && nextPosition < items.size) {
            val nextItem = items[nextPosition]
            val nextImageUrl = nextItem.images.backdrop ?: nextItem.images.primary
            val nextLogoUrl = nextItem.images.logo

            if (nextImageUrl != null || nextLogoUrl != null) {
                val urlsToPreload = listOfNotNull(nextImageUrl, nextLogoUrl)
                val context = binding.featuredLogo.context
                val imageLoader = context.imageLoader
                urlsToPreload.forEach { url ->
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    private fun updateNavigationButtons(position: Int) {
        val itemCount = featuredBannerAdapter.itemCount
        binding.btnFeaturedPrev.isEnabled = itemCount > 1
        binding.btnFeaturedNext.isEnabled = itemCount > 1
        binding.btnFeaturedPrev.isVisible = itemCount > 1
        binding.btnFeaturedNext.isVisible = itemCount > 1
    }

    private fun startAutoSlide() {
        stopAutoSlide()
        Timber.d("Starting auto slide")
        autoSlideRunnable = Runnable {
            val itemCount = featuredBannerAdapter.itemCount
            if (itemCount > 1) {
                val currentPosition = binding.featuredViewpager.currentItem
                val nextPosition = (currentPosition + 1) % itemCount
                binding.featuredViewpager.setCurrentItem(nextPosition, true)
            }
        }
        autoSlideHandler.postDelayed(autoSlideRunnable!!, autoSlideDelay)
    }

    private fun stopAutoSlide() {
        Timber.d("Stopping auto slide")
        autoSlideRunnable?.let {
            autoSlideHandler.removeCallbacks(it)
        }
    }

    private fun restartAutoSlide() {
        stopAutoSlide()
        startAutoSlide()
    }

    private fun navigateToLibraryFragment(view: dev.jdtech.jellyfin.models.View) {
        findNavController().safeNavigate(
            HomeFragmentDirections.actionNavigationHomeToLibraryFragment(
                libraryId = view.id,
                libraryName = view.name,
                libraryType = view.type,
            ),
        )
    }

    private fun navigateToMediaItem(item: FindroidItem) {
        Timber.d("Navigating to media item: ${item.id}, type: ${item::class.simpleName}")
        when (item) {
            is FindroidMovie -> {
                findNavController().safeNavigate(
                    HomeFragmentDirections.actionNavigationHomeToMovieFragment(
                        item.id,
                        item.name,
                    ),
                )
            }
            is FindroidShow -> {
                findNavController().safeNavigate(
                    HomeFragmentDirections.actionNavigationHomeToShowFragment(
                        item.id,
                        item.name,
                    ),
                )
            }
            is FindroidEpisode -> {
                findNavController().safeNavigate(
                    HomeFragmentDirections.actionNavigationHomeToEpisodeBottomSheetFragment(
                        item.id,
                    ),
                )
            }
        }
    }

    private fun navigateToSettingsFragment() {
        findNavController().safeNavigate(
            HomeFragmentDirections.actionHomeFragmentToSettingsFragment(),
        )
    }

    private fun navigateToSearchResultFragment(query: String) {
        findNavController().safeNavigate(
            HomeFragmentDirections.actionHomeFragmentToSearchResultFragment(query),
        )
    }
}
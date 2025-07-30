package dev.jdtech.jellyfin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.adapters.GenreAdapter
import dev.jdtech.jellyfin.databinding.FragmentGenreSelectionBinding
import dev.jdtech.jellyfin.models.FilterBy
import dev.jdtech.jellyfin.models.FilterItem
import dev.jdtech.jellyfin.viewmodels.GenreSelectionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class GenreSelectionFragment : Fragment() {

    private var _binding: FragmentGenreSelectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GenreSelectionViewModel by viewModels()
    private val args: GenreSelectionFragmentArgs by navArgs()
    
    @Inject
    lateinit var preferences: AppPreferences

    private lateinit var adapter: GenreAdapter

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenreSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Thiết lập toolbar
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        // Thiết lập adapter
        adapter = GenreAdapter { selectedGenre ->
            onGenreSelected(selectedGenre)
        }
        binding.genresRecyclerView.adapter = adapter

        // Thiết lập swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadGenres(args.libraryId, true) // Force refresh
        }

        // Thiết lập các nút
        binding.refreshButton.setOnClickListener {
            viewModel.loadGenres(args.libraryId, true) // Force refresh
        }

        binding.resetButton.setOnClickListener {
            resetAllFilters()
        }

        // Observe view model state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is GenreSelectionViewModel.UiState.Loading -> {
                        binding.loadingIndicator.visibility = View.VISIBLE
                        binding.emptyView.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                    }
                    is GenreSelectionViewModel.UiState.Success -> {
                        binding.loadingIndicator.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false

                        if (state.genres.isEmpty()) {
                            binding.emptyView.visibility = View.VISIBLE
                            binding.swipeRefresh.visibility = View.GONE
                        } else {
                            binding.emptyView.visibility = View.GONE
                            binding.swipeRefresh.visibility = View.VISIBLE

                            // Tạo danh sách genre với "All Genres" ở đầu
                            val genreItems = mutableListOf<FilterItem>()
                            genreItems.add(FilterItem("", getString(CoreR.string.genre_all)))
                            state.genres.forEach { genre ->
                                genreItems.add(FilterItem(genre.first, genre.second))
                            }

                            // Cập nhật adapter
                            adapter.submitList(genreItems)
                            
                            // Set selection
                            val currentGenreId = preferences.filterGenreId
                            adapter.setSelectedGenre(currentGenreId)
                        }
                    }
                    is GenreSelectionViewModel.UiState.Error -> {
                        binding.loadingIndicator.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        binding.emptyView.visibility = View.VISIBLE
                        binding.emptyView.text = state.message
                    }
                }
            }
        }

        // Load genres khi fragment được tạo
        viewModel.loadGenres(args.libraryId)

        // Thêm nút scroll to top
        binding.scrollToTopButton.setOnClickListener {
            binding.genresRecyclerView.smoothScrollToPosition(0)
        }

        // Hiển thị/ẩn nút scroll to top khi cần thiết
        binding.genresRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // Lấy vị trí hiện tại
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                // Hiển thị nút khi không ở đầu danh sách
                if (firstVisibleItemPosition > 10) {
                    binding.scrollToTopButton.show()
                } else {
                    binding.scrollToTopButton.hide()
                }
            }
        })
    }

    private fun onGenreSelected(selectedGenre: FilterItem) {
        if (selectedGenre.id.isEmpty()) {
            preferences.filterBy = FilterBy.NONE.name
            preferences.filterGenreId = null
            preferences.filterGenreName = null
            Timber.d("Cleared genre filter")
        } else {
            preferences.filterBy = FilterBy.GENRE.name
            preferences.filterGenreId = selectedGenre.id
            preferences.filterGenreName = selectedGenre.name
            Timber.d("Set genre filter to: ${selectedGenre.id}")
        }
        
        // Quay lại library để áp dụng thay đổi
        findNavController().previousBackStackEntry?.savedStateHandle?.set("FILTER_APPLIED", true)
        findNavController().popBackStack()
    }

    private fun resetAllFilters() {
        preferences.filterBy = FilterBy.NONE.name
        preferences.filterGenreId = null
        preferences.filterGenreName = null
        preferences.filterYearId = null
        preferences.filterYearName = null
        
        Timber.d("Reset all filters")
        
        // Đặt lại selection trong adapter
        adapter.setSelectedGenre(null)
        
        // Quay lại library để áp dụng thay đổi
        findNavController().previousBackStackEntry?.savedStateHandle?.set("FILTER_APPLIED", true)
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
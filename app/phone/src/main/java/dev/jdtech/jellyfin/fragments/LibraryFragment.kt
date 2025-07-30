package dev.jdtech.jellyfin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.adapters.ViewItemPagingAdapter
import dev.jdtech.jellyfin.databinding.FragmentLibraryBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.dialogs.FilterDialogFragment
import dev.jdtech.jellyfin.dialogs.SearchDialogFragment
import dev.jdtech.jellyfin.dialogs.SortDialogFragment
import dev.jdtech.jellyfin.models.FilterBy
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidFolder
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import dev.jdtech.jellyfin.utils.safeNavigate
import dev.jdtech.jellyfin.viewmodels.LibraryViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.lang.IllegalArgumentException
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private lateinit var binding: FragmentLibraryBinding
    private val viewModel: LibraryViewModel by viewModels()
    private val args: LibraryFragmentArgs by navArgs()

    private lateinit var errorDialog: ErrorDialogFragment

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cài đặt click listener cho nút More Options
        binding.moreOptionsButton.setOnClickListener { button ->
            showOptionsMenu(button)
        }

        // THÊM MỚI: Cài đặt click listener cho nút Search
        binding.searchButton.setOnClickListener {
            showSearchDialog()
        }

        binding.errorLayout.errorRetryButton.setOnClickListener {
            // Convert current filter preferences to direct parameters for retry
            val (genreIds, years) = getCurrentFilterParameters()
            viewModel.loadItems(
                args.libraryId,
                args.libraryType,
                genreIds = genreIds,
                years = years
            )
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(
                parentFragmentManager,
                ErrorDialogFragment.TAG,
            )
        }

        binding.itemsRecyclerView.adapter =
            ViewItemPagingAdapter(
                { item ->
                    navigateToItem(item)
                },
            )

        (binding.itemsRecyclerView.adapter as ViewItemPagingAdapter).addLoadStateListener {
            when (it.refresh) {
                is LoadState.Error -> {
                    val error = Exception((it.refresh as LoadState.Error).error)
                    bindUiStateError(LibraryViewModel.UiState.Error(error))
                }
                is LoadState.Loading -> {
                    bindUiStateLoading()
                }
                is LoadState.NotLoading -> {
                    binding.loadingIndicator.isVisible = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    when (uiState) {
                        is LibraryViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                        is LibraryViewModel.UiState.Loading -> bindUiStateLoading()
                        is LibraryViewModel.UiState.Error -> bindUiStateError(uiState)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (viewModel.itemsloaded) return@repeatOnLifecycle

                // Sorting options
                val sortBy = SortBy.fromString(preferences.sortBy)
                val sortOrder = try {
                    SortOrder.valueOf(preferences.sortOrder)
                } catch (e: IllegalArgumentException) {
                    SortOrder.ASCENDING
                }

                // Convert filter preferences to direct parameters
                val (genreIds, years) = getCurrentFilterParameters()

                viewModel.loadItems(
                    args.libraryId,
                    args.libraryType,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    genreIds = genreIds,
                    years = years
                )
            }
        }

        // Listen for genre selection changes from GenreSelectionFragment
        parentFragmentManager.setFragmentResultListener(
            "genre_selection",
            viewLifecycleOwner
        ) { _, bundle ->
            val selectedGenreId = bundle.getString("selectedGenreId")
            if (selectedGenreId != null) {
                preferences.filterBy = FilterBy.GENRE.name
                preferences.filterGenreId = selectedGenreId
                val (genreIds, _) = getCurrentFilterParameters()
                viewModel.loadItems(
                    args.libraryId,
                    args.libraryType,
                    genreIds = genreIds
                )
            }
        }

        // Listen for filter changes from GenreSelectionFragment
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("FILTER_APPLIED")?.observe(viewLifecycleOwner) { applied ->
            if (applied) {
                // Convert current filter preferences to direct parameters for reload
                val (genreIds, years) = getCurrentFilterParameters()
                val sortBy = SortBy.fromString(preferences.sortBy)
                val sortOrder = try {
                    SortOrder.valueOf(preferences.sortOrder)
                } catch (e: IllegalArgumentException) {
                    SortOrder.ASCENDING
                }
                
                viewModel.loadItems(
                    args.libraryId,
                    args.libraryType,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    genreIds = genreIds,
                    years = years
                )
                
                // Reset the flag
                findNavController().currentBackStackEntry?.savedStateHandle?.set("FILTER_APPLIED", false)
            }
        }
    }

    // THÊM MỚI: Phương thức để hiển thị dialog tìm kiếm
    private fun showSearchDialog() {
        val searchDialog = SearchDialogFragment { query ->
            if (!query.isNullOrEmpty()) {
                navigateToSearchResultFragment(query)
            }
        }
        searchDialog.show(parentFragmentManager, "SearchDialog")
    }

    // THÊM MỚI: Phương thức để chuyển hướng đến trang kết quả tìm kiếm
    private fun navigateToSearchResultFragment(query: String) {
        findNavController().safeNavigate(
            LibraryFragmentDirections.actionLibraryFragmentToSearchResultFragment(query)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("Fragment view destroyed")
    }

    private fun showOptionsMenu(view: View) {
        PopupMenu(requireContext(), view).apply {
            inflate(CoreR.menu.library_menu)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    CoreR.id.action_sort_by -> {
                        SortDialogFragment(
                            args.libraryId,
                            args.libraryType,
                            viewModel,
                            "sortBy",
                        ).show(
                            parentFragmentManager,
                            "sortdialog",
                        )
                        true
                    }
                    CoreR.id.action_sort_order -> {
                        SortDialogFragment(
                            args.libraryId,
                            args.libraryType,
                            viewModel,
                            "sortOrder",
                        ).show(
                            parentFragmentManager,
                            "sortdialog",
                        )
                        true
                    }
                    CoreR.id.action_filter_genre -> {
                        // Chuyển sang sử dụng GenreSelectionFragment thay vì dialog
                        findNavController().navigate(
                            LibraryFragmentDirections.actionLibraryFragmentToGenreSelectionFragment(
                                args.libraryId,
                                args.libraryType
                            )
                        )
                        true
                    }
                    CoreR.id.action_filter_year -> {
                        // Vẫn giữ nguyên sử dụng FilterDialogFragment cho year
                        FilterDialogFragment(
                            args.libraryId,
                            args.libraryType,
                            viewModel,
                            "year",
                        ).show(
                            parentFragmentManager,
                            "filterdialog",
                        )
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun getCurrentFilterParameters(): Pair<List<String>?, List<Int>?> {
        return when (FilterBy.fromString(preferences.filterBy)) {
            FilterBy.GENRE -> {
                val genreId = preferences.filterGenreId
                if (genreId.isNullOrEmpty()) {
                    Pair(null, null)
                } else {
                    Pair(listOf(genreId), null)
                }
            }
            FilterBy.YEAR -> {
                val yearId = preferences.filterYearId
                if (yearId.isNullOrEmpty()) {
                    Pair(null, null)
                } else {
                    try {
                        Pair(null, listOf(yearId.toInt()))
                    } catch (e: NumberFormatException) {
                        Pair(null, null)
                    }
                }
            }
            FilterBy.NONE -> Pair(null, null)
        }
    }

    private fun bindUiStateNormal(uiState: LibraryViewModel.UiState.Normal) {
        val adapter = binding.itemsRecyclerView.adapter as ViewItemPagingAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiState.items.collect {
                    adapter.submitData(it)
                }
            }
        }
        binding.loadingIndicator.isVisible = false
        binding.itemsRecyclerView.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateError(uiState: LibraryViewModel.UiState.Error) {
        errorDialog = ErrorDialogFragment.newInstance(uiState.error)
        binding.loadingIndicator.isVisible = false
        binding.itemsRecyclerView.isVisible = false
        binding.errorLayout.errorPanel.isVisible = true
        checkIfLoginRequired(uiState.error.message)
    }

    private fun navigateToItem(item: FindroidItem) {
        when (item) {
            is FindroidMovie -> {
                findNavController().safeNavigate(
                    LibraryFragmentDirections.actionLibraryFragmentToMovieFragment(
                        item.id,
                        item.name,
                    ),
                )
            }
            is FindroidShow -> {
                findNavController().safeNavigate(
                    LibraryFragmentDirections.actionLibraryFragmentToShowFragment(
                        item.id,
                        item.name,
                    ),
                )
            }
            is FindroidBoxSet -> {
                findNavController().safeNavigate(
                    LibraryFragmentDirections.actionLibraryFragmentToCollectionFragment(
                        item.id,
                        item.name,
                    ),
                )
            }
            is FindroidFolder -> {
                findNavController().safeNavigate(
                    LibraryFragmentDirections.actionLibraryFragmentSelf(
                        item.id,
                        item.name,
                        args.libraryType,
                    ),
                )
            }
        }
    }
}
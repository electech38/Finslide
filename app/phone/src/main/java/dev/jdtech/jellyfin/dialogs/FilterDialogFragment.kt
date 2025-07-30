package dev.jdtech.jellyfin.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FilterBy
import dev.jdtech.jellyfin.models.FilterItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.viewmodels.LibraryViewModel
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.lang.IllegalStateException
import java.util.UUID
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class FilterDialogFragment(
    private val parentId: UUID,
    private val libraryType: CollectionType,
    private val viewModel: LibraryViewModel,
    private val filterType: String, // "year" hoặc không xác định
) : DialogFragment() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = MaterialAlertDialogBuilder(it)

            when (filterType) {
                "year" -> {
                    Timber.d("Creating year dialog")
                    showYearDialog(builder)
                }
                else -> {
                    Timber.e("Invalid filterType: $filterType")
                    builder.setTitle("Lỗi")
                        .setMessage("Loại bộ lọc không hợp lệ")
                        .setPositiveButton(getString(CoreR.string.ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                }
            }

            builder.create().also {
                Timber.d("Dialog created successfully for filterType: $filterType")
                // Disable transition to reduce flicker
                it.window?.setWindowAnimations(0)
            }
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun showYearDialog(builder: MaterialAlertDialogBuilder) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val yearItems = mutableListOf<FilterItem>()
        yearItems.add(FilterItem("", getString(CoreR.string.all_years)))

        for (year in currentYear downTo 1950) {
            yearItems.add(FilterItem(year.toString(), year.toString()))
        }

        val yearNames = yearItems.map { it.name }.toTypedArray()
        val currentYearId = appPreferences.filterYearId ?: ""
        val currentIndex = yearItems.indexOfFirst { it.id == currentYearId }.let {
            if (it == -1) 0 else it
        }

        Timber.d("Displaying year dialog with ${yearItems.size} years, current: $currentYearId, index: $currentIndex")

        builder
            .setTitle(getString(CoreR.string.filter_by_year))
            .setSingleChoiceItems(yearNames, currentIndex) { dialog, which ->
                val selectedYear = yearItems[which]

                Timber.d("Selected year: '${selectedYear.id}' (${selectedYear.name})")

                if (selectedYear.id.isEmpty()) {
                    appPreferences.filterBy = FilterBy.NONE.name
                    appPreferences.filterYearId = null
                    appPreferences.filterYearName = null
                    Timber.d("Cleared year filter")
                } else {
                    appPreferences.filterBy = FilterBy.YEAR.name
                    appPreferences.filterGenreId = null
                    appPreferences.filterGenreName = null
                    appPreferences.filterYearId = selectedYear.id
                    appPreferences.filterYearName = selectedYear.name
                    Timber.d("Set year filter to: ${selectedYear.id}")
                }
                
                reloadWithCurrentSettings()
                dialog.dismiss()
            }
            .setNegativeButton(getString(CoreR.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(getString(CoreR.string.reset)) { dialog, _ ->
                // Reset tất cả bộ lọc
                appPreferences.filterBy = FilterBy.NONE.name
                appPreferences.filterGenreId = null
                appPreferences.filterGenreName = null
                appPreferences.filterYearId = null
                appPreferences.filterYearName = null
                
                Timber.d("Reset all filters - new state: filterBy=${appPreferences.filterBy}, genreId=${appPreferences.filterGenreId}, yearId=${appPreferences.filterYearId}")
                
                reloadWithCurrentSettings()
                dialog.dismiss()
            }
    }

    private fun reloadWithCurrentSettings() {
        Timber.d("Reloading with current filter settings... filterBy: ${appPreferences.filterBy}, genreId: ${appPreferences.filterGenreId}, yearId: ${appPreferences.filterYearId}")

        val sortBy = SortBy.fromString(appPreferences.sortBy)
        val sortOrder = try {
            SortOrder.valueOf(appPreferences.sortOrder)
        } catch (e: IllegalArgumentException) {
            SortOrder.ASCENDING
        }

        val (genreIds, years) = when (FilterBy.fromString(appPreferences.filterBy)) {
            FilterBy.GENRE -> {
                val genreId = appPreferences.filterGenreId
                if (genreId.isNullOrEmpty()) {
                    Timber.d("No genre filter applied")
                    Pair(null, null)
                } else {
                    Timber.d("Applying genre filter with genreId: $genreId")
                    Pair(listOf(genreId), null)
                }
            }
            FilterBy.YEAR -> {
                val yearId = appPreferences.filterYearId
                if (yearId.isNullOrEmpty()) {
                    Timber.d("No year filter applied")
                    Pair(null, null)
                } else {
                    try {
                        val year = yearId.toInt()
                        Timber.d("Applying year filter: $year")
                        Pair(null, listOf(year))
                    } catch (e: NumberFormatException) {
                        Timber.e("Invalid year format: $yearId")
                        Pair(null, null)
                    }
                }
            }
            FilterBy.NONE -> {
                Timber.d("No filter applied")
                Pair(null, null)
            }
        }

        Timber.d("Calling loadItems with genreIds: $genreIds, years: $years")
        viewModel.loadItems(parentId, libraryType, sortBy, sortOrder, genreIds, years)
    }
}
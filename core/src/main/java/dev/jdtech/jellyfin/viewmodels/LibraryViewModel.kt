package dev.jdtech.jellyfin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.repository.JellyfinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _genres = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val genres = _genres.asStateFlow()

    var itemsloaded = false

    sealed class UiState {
        data class Normal(val items: Flow<PagingData<FindroidItem>>) : UiState()
        data object Loading : UiState()
        data class Error(val error: Exception) : UiState()
    }

    fun loadItems(
        parentId: UUID,
        libraryType: CollectionType,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        genreIds: List<String>? = null,
        years: List<Int>? = null
    ) {
        itemsloaded = true
        Timber.d("Loading items for libraryType: $libraryType, genreIds: $genreIds, years: $years")
        val itemType = when (libraryType) {
            CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
            CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
            CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
            CollectionType.Mixed -> listOf(BaseItemKind.FOLDER, BaseItemKind.MOVIE, BaseItemKind.SERIES)
            else -> null
        }

        val recursive = itemType == null || !itemType.contains(BaseItemKind.FOLDER)

        viewModelScope.launch {
            _uiState.emit(UiState.Loading)
            try {
                val items = jellyfinRepository.getItemsPaging(
                    parentId = parentId,
                    includeTypes = itemType,
                    recursive = recursive,
                    sortBy = if (libraryType == CollectionType.TvShows && sortBy == SortBy.DATE_PLAYED) SortBy.SERIES_DATE_PLAYED else sortBy,
                    sortOrder = sortOrder,
                    genreIds = genreIds,
                    years = years
                ).cachedIn(viewModelScope)
                _uiState.emit(UiState.Normal(items))
            } catch (e: Exception) {
                Timber.e(e, "Error loading items")
                _uiState.emit(UiState.Error(e))
            }
        }
    }

    fun loadGenres(parentId: UUID) {
        viewModelScope.launch {
            try {
                Timber.d("Loading genres for parentId: $parentId")
                val genres = jellyfinRepository.getGenres(parentId)
                if (genres.isEmpty()) {
                    Timber.w("No genres loaded for parentId: $parentId")
                } else {
                    Timber.d("Loaded ${genres.size} genres: $genres")
                }
                _genres.value = genres
            } catch (e: Exception) {
                Timber.e(e, "Error loading genres for parentId: $parentId")
                _genres.value = emptyList()
            }
        }
    }
    
    // Thêm hàm mới này để hỗ trợ setGenres từ bên ngoài
    fun setGenres(genres: List<Pair<String, String>>) {
        Timber.d("Setting genres directly: ${genres.size} genres")
        _genres.value = genres
    }
}
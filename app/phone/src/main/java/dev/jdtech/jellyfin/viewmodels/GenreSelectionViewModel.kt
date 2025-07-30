package dev.jdtech.jellyfin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GenreSelectionViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Cache cho danh sách genre
    private val genreCache = mutableMapOf<UUID, List<Pair<String, String>>>()

    fun loadGenres(parentId: UUID, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                // Kiểm tra cache trừ khi yêu cầu refresh
                val cachedGenres = genreCache[parentId]
                if (!forceRefresh && cachedGenres != null) {
                    Timber.d("Using cached genres for parentId: $parentId")
                    _uiState.value = UiState.Success(cachedGenres)
                    return@launch
                }

                Timber.d("Loading genres for parentId: $parentId")
                val genres = jellyfinRepository.getGenres(parentId)
                
                if (genres.isEmpty()) {
                    Timber.w("No genres loaded for parentId: $parentId")
                    _uiState.value = UiState.Success(emptyList())
                } else {
                    Timber.d("Loaded ${genres.size} genres")
                    genreCache[parentId] = genres
                    _uiState.value = UiState.Success(genres)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading genres for parentId: $parentId")
                _uiState.value = UiState.Error("Không tải được danh sách thể loại")
            }
        }
    }

    fun clearCache(parentId: UUID? = null) {
        if (parentId != null) {
            genreCache.remove(parentId)
        } else {
            genreCache.clear()
        }
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val genres: List<Pair<String, String>>) : UiState() 
        data class Error(val message: String) : UiState()
    }
}
package dev.jdtech.jellyfin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.toView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject internal constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    sealed class UiState {
        data class Normal(val homeItems: List<HomeItem>) : UiState()
        data object Loading : UiState()
        data class Error(val error: Exception) : UiState()
    }

    private val uuidContinueWatching = UUID(4937169328197226115, -4704919157662094443) // 44845958-8326-4e83-beb4-c4f42e9eeb95
    private val uiTextContinueWatching = UiText.StringResource(R.string.continue_watching)

    private var cachedHomeItems: List<HomeItem>? = null
    private var lastCacheTime = 0L
    private val cacheValidDuration = 5 * 60 * 1000L // 5 phút cho hầu hết nội dung
    private val quickChangingCacheDuration = 0 * 1000L // 10 giây cho Continue Watching

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.emit(UiState.Loading)

            val items = mutableListOf<HomeItem>()
            if (appPreferences.offlineMode) items.add(HomeItem.OfflineCard)

            try {
                val currentTime = System.currentTimeMillis()
                val isCacheValid = (currentTime - lastCacheTime) < cacheValidDuration
                val cachedHomeItemsList = cachedHomeItems

                // Tách riêng Continue Watching để luôn refresh
                if (!forceRefresh && cachedHomeItemsList != null && isCacheValid) {
                    Timber.d("Using cached home items: ${cachedHomeItemsList.size}")
                    
                    // Luôn tải mới Continue Watching, giữ các section khác từ cache
                    viewModelScope.launch {
                        try {
                            val resumeItems = withTimeoutOrNull(10000) {
                                repository.getResumeItems().take(100)
                            } ?: emptyList()
                            
                            if (resumeItems.isNotEmpty()) {
                                val updatedItems = cachedHomeItemsList.toMutableList()
                                
                                // Tìm và cập nhật section Continue Watching
                                val continueWatchingIndex = updatedItems.indexOfFirst { 
                                    it is HomeItem.Section && 
                                    (it as HomeItem.Section).homeSection.id == uuidContinueWatching 
                                }
                                
                                val newSection = HomeItem.Section(
                                    HomeSection(uuidContinueWatching, uiTextContinueWatching, resumeItems)
                                )
                                
                                if (continueWatchingIndex != -1) {
                                    // Cập nhật section hiện có
                                    updatedItems[continueWatchingIndex] = newSection
                                    Timber.d("Realtime update of Continue Watching")
                                } else {
                                    // Thêm section mới
                                    val insertIndex = if (updatedItems.firstOrNull() is HomeItem.OfflineCard) 1 else 0
                                    updatedItems.add(insertIndex, newSection)
                                    Timber.d("Added new Continue Watching section")
                                }
                                
                                _uiState.emit(UiState.Normal(updatedItems))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to refresh Continue Watching")
                        }
                    }
                    
                    _uiState.emit(UiState.Normal(cachedHomeItemsList))
                    return@launch
                }

                // Clear cache nếu force refresh
                if (forceRefresh) {
                    Timber.d("Force refresh - clearing cache")
                    cachedHomeItems = null
                    lastCacheTime = 0L
                }

                // Load data mới
                Timber.d("Loading fresh data...")
                val updated = coroutineScope {
                    val dynamicItemsDeferred = async { loadDynamicItems() }
                    val viewsDeferred = async { loadViews() }
                    val dynamicItems = withTimeoutOrNull(15000) { dynamicItemsDeferred.await() } ?: emptyList()
                    val views = withTimeoutOrNull(15000) { viewsDeferred.await() } ?: emptyList()
                    items + dynamicItems + views
                }

                // Lưu vào cache với timestamp
                cachedHomeItems = updated
                lastCacheTime = currentTime
                Timber.d("Loaded ${updated.size} home items (Dynamic: ${updated.count { it is HomeItem.Section }}, Views: ${updated.count { it is HomeItem.ViewItem }})")
                _uiState.emit(UiState.Normal(updated))
            } catch (e: Exception) {
                Timber.e(e, "Failed to load data")
                // Retry một lần cho network issues
                try {
                    Timber.d("Retrying data load...")
                    val updated = coroutineScope {
                        val dynamicItemsDeferred = async { loadDynamicItems() }
                        val viewsDeferred = async { loadViews() }
                        val dynamicItems = withTimeoutOrNull(10000) { dynamicItemsDeferred.await() } ?: emptyList()
                        val views = withTimeoutOrNull(10000) { viewsDeferred.await() } ?: emptyList()
                        items + dynamicItems + views
                    }
                    cachedHomeItems = updated
                    lastCacheTime = System.currentTimeMillis()
                    Timber.d("Retry succeeded, loaded ${updated.size} home items")
                    _uiState.emit(UiState.Normal(updated))
                } catch (retryException: Exception) {
                    Timber.e(retryException, "Retry failed")
                    _uiState.emit(UiState.Error(retryException))
                }
            }
        }
    }

    private suspend fun loadDynamicItems(): List<HomeItem.Section> {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<HomeSection>()

        val resumeItems = withTimeoutOrNull(15000) {
            Timber.d("Loading resume items")
            repository.getResumeItems().take(100) // Tăng từ 50 lên 100
        } ?: emptyList()

        if (resumeItems.isNotEmpty()) {
            items.add(
                HomeSection(
                    uuidContinueWatching,
                    uiTextContinueWatching,
                    resumeItems,
                ),
            )
        }

        Timber.d("Loaded dynamic items in ${System.currentTimeMillis() - startTime}ms (${resumeItems.size} resume items)")
        return items.map { HomeItem.Section(it) }
    }

    private suspend fun loadViews(): List<HomeItem.ViewItem> {
        val startTime = System.currentTimeMillis()
        val views = withTimeoutOrNull(15000) {
            repository
                .getUserViews()
                .filter { view -> CollectionType.fromString(view.collectionType?.serialName) in CollectionType.supported }
        } ?: emptyList()

        Timber.d("Found ${views.size} supported views: ${views.map { it.name }}")

        val result = coroutineScope {
            views.map { view ->
                async {
                    try {
                        Timber.d("Loading latest media for view ${view.name}")
                        val latest = withTimeoutOrNull(15000) {
                            repository.getLatestMedia(view.id).take(100) // Tăng từ 50 lên 100
                        } ?: emptyList()

                        Timber.d("Loaded ${latest.size} items for view ${view.name}")

                        if (latest.isNotEmpty()) {
                            view.toView().apply { items = latest }
                        } else {
                            Timber.w("No items found for view ${view.name}")
                            // Still return view even if no items to show library structure
                            view.toView().apply { items = emptyList() }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading view ${view.name}")
                        null
                    }
                }
            }.mapNotNull {
                val result = it.await()
                result // Return all views, even empty ones
            }
        }

        Timber.d("Loaded ${result.size} views with content in ${System.currentTimeMillis() - startTime}ms")
        return result.map { HomeItem.ViewItem(it) }
    }

    // Method để clear cache từ bên ngoài nếu cần
    fun clearCache() {
        cachedHomeItems = null
        lastCacheTime = 0L
        Timber.d("Cache cleared manually")
    }
}
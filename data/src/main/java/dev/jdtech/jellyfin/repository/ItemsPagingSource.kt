package dev.jdtech.jellyfin.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

class ItemsPagingSource(
    private val jellyfinRepository: JellyfinRepository,
    private val parentId: UUID?,
    private val includeTypes: List<BaseItemKind>?,
    private val recursive: Boolean,
    private val sortBy: SortBy,
    private val sortOrder: SortOrder,
    private val genreIds: List<String>?,
    private val years: List<Int>?
) : PagingSource<Int, FindroidItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FindroidItem> {
        val position = params.key ?: 0

        Timber.d("ItemsPagingSource - Đang lấy vị trí: $position")
        Timber.d("ItemsPagingSource - genreIds: $genreIds")
        Timber.d("ItemsPagingSource - years: $years")

        return try {
            val items = jellyfinRepository.getItems(
                parentId = parentId,
                includeTypes = includeTypes,
                recursive = recursive,
                sortBy = sortBy,
                sortOrder = sortOrder,
                startIndex = position,
                limit = params.loadSize,
                genreIds = genreIds,
                years = years
            )

            Timber.d("ItemsPagingSource - Đã tải ${items.size} mục")

            LoadResult.Page(
                data = items,
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (items.isEmpty()) null else position + params.loadSize,
            )
        } catch (e: Exception) {
            Timber.e(e, "ItemsPagingSource - Lỗi khi tải mục")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FindroidItem>): Int {
        return 0
    }
}
package dev.jdtech.jellyfin.models

data class LibraryFilter(
    val filterBy: FilterBy = FilterBy.NONE,
    val selectedFilterId: String? = null,
    val selectedFilterName: String? = null
)
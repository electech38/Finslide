package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.toFindroidCollection
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidItem
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidSeason
import dev.jdtech.jellyfin.models.toFindroidSegment
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.toFindroidSource
import io.ktor.util.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.UserConfiguration
import timber.log.Timber
import java.io.File
import java.util.UUID

class JellyfinRepositoryImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : JellyfinRepository {
    init {
        Timber.d("Initialized JellyfinRepositoryImpl, offlineMode: ${appPreferences.offlineMode}")
    }

    override suspend fun getPublicSystemInfo(): PublicSystemInfo = withContext(Dispatchers.IO) {
        jellyfinApi.systemApi.getPublicSystemInfo().content
    }

    override suspend fun getUserViews(): List<BaseItemDto> = withContext(Dispatchers.IO) {
        jellyfinApi.viewsApi.getUserViews(jellyfinApi.userId!!).content.items.orEmpty()
    }

    override suspend fun getItem(itemId: UUID): BaseItemDto = withContext(Dispatchers.IO) {
        jellyfinApi.userLibraryApi.getItem(itemId, jellyfinApi.userId!!).content
    }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi.getItem(
                itemId,
                jellyfinApi.userId!!,
            ).content.toFindroidEpisode(this@JellyfinRepositoryImpl, database)!!
        }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi.getItem(
                itemId,
                jellyfinApi.userId!!,
            ).content.toFindroidMovie(this@JellyfinRepositoryImpl, database)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi.getItem(
                itemId,
                jellyfinApi.userId!!,
            ).content.toFindroidShow(this@JellyfinRepositoryImpl)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi.getItem(
                itemId,
                jellyfinApi.userId!!,
            ).content.toFindroidSeason(this@JellyfinRepositoryImpl)
        }

    override suspend fun getLibraries(): List<FindroidCollection> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi.getItems(
                jellyfinApi.userId!!,
            ).content.items
                .orEmpty()
                .mapNotNull { it.toFindroidCollection(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
        genreIds: List<String>?,
        years: List<Int>?
    ): List<FindroidItem> = withContext(Dispatchers.IO) {
        Timber.d("getItems called with parentId: $parentId, genreIds: $genreIds, years: $years, includeTypes: $includeTypes")

        val response = jellyfinApi.itemsApi.getItems(
            userId = jellyfinApi.userId!!,
            parentId = parentId,
            includeItemTypes = includeTypes,
            recursive = recursive,
            sortBy = listOf(ItemSortBy.fromName(sortBy.sortString)),
            sortOrder = listOf(sortOrder),
            startIndex = startIndex,
            limit = limit,
            genres = genreIds, // Use original genre names
            years = years
        )

        val items = response.content.items.orEmpty()
        Timber.d("API returned ${items.size} items for genres: $genreIds")

        items.mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
    }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        genreIds: List<String>?,
        years: List<Int>?
    ): Flow<PagingData<FindroidItem>> {
        Timber.d("getItemsPaging called with parentId: $parentId, genreIds: $genreIds, years: $years")

        return Pager(
            config = PagingConfig(
                pageSize = 20, // Increase page size for faster loading
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                ItemsPagingSource(
                    this,
                    parentId,
                    includeTypes,
                    recursive,
                    sortBy,
                    sortOrder,
                    genreIds,
                    years
                )
            },
        ).flow
    }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> = withContext(Dispatchers.IO) {
        jellyfinApi.itemsApi.getItems(
            jellyfinApi.userId!!,
            personIds = personIds,
            includeItemTypes = includeTypes,
            recursive = recursive,
        ).content.items
            .orEmpty()
            .mapNotNull {
                it.toFindroidItem(this@JellyfinRepositoryImpl, database)
            }
    }

    override suspend fun getFavoriteItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi.getItems(
                jellyfinApi.userId!!,
                filters = listOf(ItemFilter.IS_FAVORITE),
                includeItemTypes = listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                ),
                recursive = true,
            ).content.items
                .orEmpty()
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSearchItems(searchQuery: String): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi.getItems(
                jellyfinApi.userId!!,
                searchTerm = searchQuery,
                includeItemTypes = listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                ),
                recursive = true,
            ).content.items
                .orEmpty()
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getResumeItems(): List<FindroidItem> {
        val items = withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi.getResumeItems(
                jellyfinApi.userId!!,
                limit = 12,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
            ).content.items.orEmpty()
        }
        return items.mapNotNull {
            it.toFindroidItem(this, database)
        }
    }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> {
        val items = withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi.getLatestMedia(
                jellyfinApi.userId!!,
                parentId = parentId,
                limit = 16,
            ).content
        }
        return items.mapNotNull {
            it.toFindroidItem(this, database)
        }
    }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showsApi.getSeasons(seriesId, jellyfinApi.userId!!).content.items
                    .orEmpty()
                    .map { it.toFindroidSeason(this@JellyfinRepositoryImpl) }
            } else {
                database.getSeasonsByShowId(seriesId).map { it.toFindroidSeason(database, jellyfinApi.userId!!) }
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showsApi.getNextUp(
                jellyfinApi.userId!!,
                limit = 24,
                seriesId = seriesId,
                enableResumable = false,
            ).content.items
                .orEmpty()
                .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showsApi.getEpisodes(
                    seriesId,
                    jellyfinApi.userId!!,
                    seasonId = seasonId,
                    fields = fields,
                    startItemId = startItemId,
                    limit = limit,
                ).content.items
                    .orEmpty()
                    .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl, database) }
            } else {
                database.getEpisodesBySeasonId(seasonId).map { it.toFindroidEpisode(database, jellyfinApi.userId!!) }
            }
        }

    override suspend fun getMediaSources(itemId: UUID, includePath: Boolean): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            val sources = mutableListOf<FindroidSource>()
            sources.addAll(
                jellyfinApi.mediaInfoApi.getPostedPlaybackInfo(
                    itemId,
                    PlaybackInfoDto(
                        userId = jellyfinApi.userId!!,
                        deviceProfile = DeviceProfile(
                            name = "Direct play all",
                            maxStaticBitrate = 1_000_000_000,
                            maxStreamingBitrate = 1_000_000_000,
                            codecProfiles = emptyList(),
                            containerProfiles = emptyList(),
                            directPlayProfiles = listOf(
                                DirectPlayProfile(type = DlnaProfileType.VIDEO),
                                DirectPlayProfile(type = DlnaProfileType.AUDIO),
                            ),
                            transcodingProfiles = emptyList(),
                            subtitleProfiles = listOf(
                                SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                                SubtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL),
                            )
                        ),
                        maxStreamingBitrate = 1_000_000_000,
                    ),
                ).content.mediaSources.map {
                    it.toFindroidSource(
                        this@JellyfinRepositoryImpl,
                        itemId,
                        includePath,
                    )
                },
            )
            sources.addAll(
                database.getSources(itemId).map { it.toFindroidSource(database) },
            )
            sources
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videosApi.getVideoStreamUrl(
                    itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                )
            } catch (e: Exception) {
                Timber.e(e)
                ""
            }
        }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) {
            val segments = database.getSegments(itemId).map { it.toFindroidSegment() }

            if (segments.isNotEmpty()) {
                return@withContext segments
            }

            try {
                val segmentsMap = jellyfinApi.api.get<Map<String, FindroidSegment>>(
                    pathTemplate = "/Episode/{itemId}/IntroSkipperSegments",
                    pathParameters = mapOf("itemId" to itemId),
                ).content

                for ((type, segment) in segmentsMap) {
                    segment.type = when (type) {
                        "Introduction" -> FindroidSegmentType.INTRO
                        "Credits" -> FindroidSegmentType.CREDITS
                        else -> FindroidSegmentType.UNKNOWN
                    }
                }

                Timber.tag("SegmentInfo").d("segments: %s", segmentsMap.values)

                return@withContext segmentsMap.values.toList()
            } catch (e: Exception) {
                Timber.e(e)
                return@withContext emptyList()
            }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                try {
                    val sources = File(context.filesDir, "trickplay/$itemId").listFiles()
                    if (sources != null) {
                        return@withContext File(sources.first(), index.toString()).readBytes()
                    }
                } catch (_: Exception) { }

                return@withContext jellyfinApi.trickplayApi.getTrickplayTileImage(itemId, width, index).content.toByteArray()
            } catch (e: Exception) {
                return@withContext null
            }
        }

    override suspend fun postCapabilities() {
        Timber.d("Sending capabilities")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands = listOf(
                    GeneralCommandType.VOLUME_UP,
                    GeneralCommandType.VOLUME_DOWN,
                    GeneralCommandType.TOGGLE_MUTE,
                    GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                    GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                    GeneralCommandType.MUTE,
                    GeneralCommandType.UNMUTE,
                    GeneralCommandType.SET_VOLUME,
                    GeneralCommandType.DISPLAY_MESSAGE,
                    GeneralCommandType.PLAY,
                    GeneralCommandType.PLAY_STATE,
                    GeneralCommandType.PLAY_NEXT,
                    GeneralCommandType.PLAY_MEDIA_SOURCE,
                ),
                supportsMediaControl = true,
            )
        }
    }

    override suspend fun postPlaybackStart(itemId: UUID) {
        Timber.d("Sending playback start $itemId")
        withContext(Dispatchers.IO) {
            jellyfinApi.playStateApi.onPlaybackStart(itemId)
        }
    }

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        Timber.d("Sending playback stop $itemId")
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
            }
            try {
                jellyfinApi.playStateApi.onPlaybackStopped(
                    itemId,
                    positionTicks = positionTicks,
                )
            } catch (e: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        Timber.d("Sending playback progress for $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            try {
                jellyfinApi.playStateApi.onPlaybackProgress(
                    itemId,
                    positionTicks = positionTicks,
                    isPaused = isPaused,
                )
            } catch (e: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userLibraryApi.markFavoriteItem(itemId)
            } catch (e: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userLibraryApi.unmarkFavoriteItem(itemId)
            } catch (e: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.playStateApi.markPlayedItem(itemId)
            } catch (e: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.playStateApi.markUnplayedItem(itemId)
            } catch (e: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override fun getBaseUrl() = jellyfinApi.api.baseUrl.orEmpty()

    override suspend fun updateDeviceName(name: String) {
        jellyfinApi.jellyfin.deviceInfo?.id?.let { id ->
            withContext(Dispatchers.IO) {
                jellyfinApi.devicesApi.updateDeviceOptions(
                    id,
                    DeviceOptionsDto(0, customName = name),
                )
            }
        }
    }

    override suspend fun getUserConfiguration(): UserConfiguration? = withContext(Dispatchers.IO) {
        jellyfinApi.userApi.getCurrentUser().content.configuration
    }

    override suspend fun getDownloads(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<FindroidItem>()
            items.addAll(
                database.getMoviesByServerId(appPreferences.currentServer!!)
                    .map { it.toFindroidMovie(database, jellyfinApi.userId!!) },
            )
            items.addAll(
                database.getShowsByServerId(appPreferences.currentServer!!)
                    .map { it.toFindroidShow(database, jellyfinApi.userId!!) },
            )
            items
        }

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }

    override suspend fun getGenres(parentId: UUID?): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Fetching genres for parentId: $parentId")
            val response = jellyfinApi.itemsApi.getItems(
                userId = jellyfinApi.userId!!,
                parentId = parentId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                fields = listOf(ItemFields.GENRES),
                limit = 50 // Reduced limit for faster loading
            )

            Timber.d("Genres API response: status=${response.status}, headers=${response.headers}")

            val items = response.content.items.orEmpty()
            if (items.isEmpty()) {
                Timber.w("No items returned for parentId: $parentId")
                return@withContext emptyList()
            }

            // Extract unique genres from items
            val genres = items
                .flatMap { it.genres.orEmpty() }
                .distinct()
                .map { genreName ->
                    Pair(genreName, genreName) // Use original genre name for both ID and display
                }

            if (genres.isEmpty()) {
                Timber.w("No genres found in items for parentId: $parentId")
            } else {
                Timber.d("Extracted genres: $genres")
            }

            genres
        } catch (e: Exception) {
            Timber.e(e, "Failed to load genres for parentId: $parentId")
            throw RuntimeException("Failed to load genres", e) // Throw for debugging
        }
    }
}
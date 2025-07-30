package dev.jdtech.jellyfin.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.ExternalSubtitle
import dev.jdtech.jellyfin.models.FindroidChapter
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.PlayerChapter
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject internal constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {
    private val eventsChannel = Channel<PlayerItemsEvent>()
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    private val cachedPlayerItems = mutableMapOf<String, List<PlayerItem>>()
    private val cachedMediaSources = mutableMapOf<String, List<FindroidSource>>()

    fun loadPlayerItems(
        item: FindroidItem,
        mediaSourceIndex: Int? = null,
    ) {
        Timber.d("Loading player items for item ${item.id}")
        viewModelScope.launch(Dispatchers.IO) {
            val playbackPosition = item.playbackPositionTicks.div(10000)

            try {
                cachedPlayerItems[item.id.toString()]?.let {
                    Timber.d("Using cached player items for ${item.id}")
                    eventsChannel.send(PlayerItemsEvent.PlayerItemsReady(it))
                    return@launch
                }

                val startTime = System.currentTimeMillis()
                val items = prepareMediaPlayerItems(item, playbackPosition, mediaSourceIndex)
                Timber.d("Loaded player items in ${System.currentTimeMillis() - startTime}ms")

                cachedPlayerItems[item.id.toString()] = items
                eventsChannel.send(PlayerItemsEvent.PlayerItemsReady(items))
            } catch (e: Exception) {
                Timber.e(e, "Failed to load player items for ${item.id}: ${e.message}")
                eventsChannel.send(PlayerItemsEvent.PlayerItemsError(e))
            }
        }
    }

    fun preloadMediaSources(items: List<FindroidItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { item ->
                if (!cachedMediaSources.containsKey(item.id.toString())) {
                    Timber.d("Preloading media sources for ${item.id}")
                    try {
                        val sources = withTimeoutOrNull(5000) {
                            repository.getMediaSources(item.id, true)
                        } ?: emptyList()
                        cachedMediaSources[item.id.toString()] = sources
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to preload media sources for ${item.id}")
                    }
                }
            }
        }
    }

    private suspend fun prepareMediaPlayerItems(
        item: FindroidItem,
        playbackPosition: Long,
        mediaSourceIndex: Int?,
    ): List<PlayerItem> = when (item) {
        is FindroidMovie -> movieToPlayerItem(item, playbackPosition, mediaSourceIndex)
        is FindroidShow -> seriesToPlayerItems(item, playbackPosition, mediaSourceIndex)
        is FindroidSeason -> seasonToPlayerItems(item, playbackPosition, mediaSourceIndex)
        is FindroidEpisode -> episodeToPlayerItems(item, playbackPosition, mediaSourceIndex)
        else -> emptyList()
    }

    private suspend fun movieToPlayerItem(
        item: FindroidMovie,
        playbackPosition: Long,
        mediaSourceIndex: Int?,
    ) = listOf(item.toPlayerItem(mediaSourceIndex, playbackPosition))

    private suspend fun seriesToPlayerItems(
        item: FindroidShow,
        playbackPosition: Long,
        mediaSourceIndex: Int?,
    ): List<PlayerItem> {
        Timber.d("Loading seasons for show ${item.id}")
        return repository
            .getSeasons(item.id)
            .flatMap { seasonToPlayerItems(it, playbackPosition, mediaSourceIndex) }
    }

    private suspend fun seasonToPlayerItems(
        item: FindroidSeason,
        playbackPosition: Long,
        mediaSourceIndex: Int?,
    ): List<PlayerItem> {
        Timber.d("Loading episodes for season ${item.id}")
        val startTime = System.currentTimeMillis()
        val episodes = repository
            .getEpisodes(
                seriesId = item.seriesId,
                seasonId = item.id,
                fields = listOf(ItemFields.MEDIA_SOURCES),
            )
            .filter { it.sources.isNotEmpty() }
            .filter { !it.missing }
            .map { episode -> episode.toPlayerItem(mediaSourceIndex, playbackPosition) }
        Timber.d("Loaded episodes in ${System.currentTimeMillis() - startTime}ms")
        return episodes
    }

    private suspend fun episodeToPlayerItems(
        item: FindroidEpisode,
        playbackPosition: Long,
        mediaSourceIndex: Int?,
    ): List<PlayerItem> {
        Timber.d("Loading episode ${item.id}")
        val startTime = System.currentTimeMillis()
        val episodes = repository
            .getEpisodes(
                seriesId = item.seriesId,
                seasonId = item.seasonId,
                fields = listOf(ItemFields.MEDIA_SOURCES),
                startItemId = item.id,
                limit = 1,
            )
            .filter { it.sources.isNotEmpty() }
            .filter { !it.missing }
            .map { episode -> episode.toPlayerItem(mediaSourceIndex, playbackPosition) }
        Timber.d("Loaded episode in ${System.currentTimeMillis() - startTime}ms")
        return episodes
    }

    private suspend fun FindroidItem.toPlayerItem(
        mediaSourceIndex: Int?,
        playbackPosition: Long,
    ): PlayerItem {
        Timber.d("Converting item ${id} to PlayerItem")
        val startTime = System.currentTimeMillis()

        val mediaSources = cachedMediaSources[id.toString()] ?: run {
            var sources: List<FindroidSource> = emptyList()
            repeat(2) { // Retry tối đa 2 lần
                sources = withTimeoutOrNull(5000) {
                    repository.getMediaSources(id, true)
                } ?: emptyList()
                if (sources.isNotEmpty()) return@run sources
            }
            if (sources.isEmpty()) throw IllegalStateException("No media sources available")
            cachedMediaSources[id.toString()] = sources
            sources
        }

        val mediaSource = if (mediaSourceIndex == null) {
            mediaSources.firstOrNull() ?: throw IllegalStateException("No media sources available")
        } else {
            mediaSources[mediaSourceIndex]
        }

        val externalSubtitles = mediaSource.mediaStreams?.filter { mediaStream ->
            mediaStream.isExternal && mediaStream.type == MediaStreamType.SUBTITLE && mediaStream.path != null
        }?.mapNotNull { mediaStream ->
            mediaStream.path?.let { path ->
                ExternalSubtitle(
                    title = mediaStream.displayTitle ?: "",
                    language = mediaStream.language ?: "",
                    uri = Uri.parse(path),
                    mimeType = when (mediaStream.codec?.lowercase()) {
                        "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
                        "webvtt", "vtt" -> MimeTypes.TEXT_VTT
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.TEXT_UNKNOWN
                    },
                )
            }
        } ?: emptyList()

        val result = PlayerItem(
            name = name,
            itemId = id,
            mediaSourceId = mediaSource.id,
            mediaSourceUri = mediaSource.path ?: throw IllegalStateException("Media source path is null"),
            playbackPosition = playbackPosition,
            parentIndexNumber = if (this is FindroidEpisode) parentIndexNumber else null,
            indexNumber = if (this is FindroidEpisode) indexNumber else null,
            indexNumberEnd = if (this is FindroidEpisode) indexNumberEnd else null,
            externalSubtitles = externalSubtitles,
            chapters = emptyList(),
        )

        Timber.d("Converted item to PlayerItem in ${System.currentTimeMillis() - startTime}ms")
        return result
    }

    private fun List<FindroidChapter>?.toPlayerChapters(): List<PlayerChapter>? {
        return this?.map { chapter ->
            PlayerChapter(
                startPosition = chapter.startPosition,
                name = chapter.name,
            )
        }
    }
}

sealed interface PlayerItemsEvent {
    data class PlayerItemsReady(val items: List<PlayerItem>) : PlayerItemsEvent
    data class PlayerItemsError(val error: Exception) : PlayerItemsEvent
}
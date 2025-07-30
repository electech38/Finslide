package dev.jdtech.jellyfin.mpv

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Size
import androidx.media3.common.util.Util
import dev.jdtech.mpv.MPVLib
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet

@Suppress("SpellCheckingInspection")
class MPVPlayer(
    context: Context,
    private val requestAudioFocus: Boolean,
    private var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.Builder(context).build(),
    private val seekBackIncrement: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS,
    private val seekForwardIncrement: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
    videoOutput: String = "gpu-next",
    audioOutput: String = "audiotrack",
    hwDec: String = "mediacodec-copy", // 🚀 OPTIMIZED: Use copy for better compatibility
) : BasePlayer(), MPVLib.EventObserver, AudioManager.OnAudioFocusChangeListener {

    private val audioManager: AudioManager by lazy { context.getSystemService()!! }
    private var audioFocusCallback: () -> Unit = {}
    private var currentIndex = 0
    private lateinit var audioFocusRequest: AudioFocusRequest
    private val handler = Handler(context.mainLooper)

    // 🚀 OPTIMIZED: Performance monitoring properties
    private var cacheSpeed: Double = 0.0
    private var bufferHealth: Double = 0.0

    init {
        require(context is Application)
        val mpvDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "mpv")
        Timber.i("🚀 MPV config dir: $mpvDir")
        if (!mpvDir.exists()) mpvDir.mkdirs()
        arrayOf("mpv.conf", "subfont.ttf").forEach { fileName ->
            val file = File(mpvDir, fileName)
            if (file.exists()) return@forEach
            context.assets.open(fileName, AssetManager.ACCESS_STREAMING)
                .copyTo(FileOutputStream(file))
        }
        MPVLib.create(context)

        // 🚀 IMPORTANT: This MPVPlayer.kt is optimized to work WITH your mpv.conf
        // The settings below complement and fine-tune based on device capabilities
        // Your mpv.conf provides the base optimizations, code provides dynamic adjustments

        // 🚀 OPTIMIZED: General settings with performance improvements
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", mpvDir.path)
        MPVLib.setOptionString("profile", "fast") // 🚀 Use fast profile
        MPVLib.setOptionString("vo", videoOutput)
        MPVLib.setOptionString("ao", audioOutput)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("vid", "no")

        // 🚀 OPTIMIZED: Enhanced Hardware video decoding
        MPVLib.setOptionString("hwdec", hwDec)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")

        // 🚀 OPTIMIZED: Performance settings (complement your mpv.conf)
        MPVLib.setOptionString("video-sync", "display-resample")
        MPVLib.setOptionString("interpolation", "no") // Disable for performance
        MPVLib.setOptionString("dither-depth", "no") // From your config
        MPVLib.setOptionString("vd-lavc-threads", "0") // Auto-detect cores
        MPVLib.setOptionString("demuxer-thread", "yes") // From your config

        // 🚀 OPTIMIZED: Audio settings (from your mpv.conf)
        MPVLib.setOptionString("audio-channels", "stereo")
        MPVLib.setOptionString("audio-samplerate", "48000")
        MPVLib.setOptionString("audio-format", "float")
        MPVLib.setOptionString("audio-buffer", "0.2")

        // 🚀 OPTIMIZED: Network & TLS settings (complement your mpv.conf)
        MPVLib.setOptionString("tls-verify", "no")
        MPVLib.setOptionString("user-agent", "Findroid MPV/4K")
        MPVLib.setOptionString("network-timeout", "10")
        MPVLib.setOptionString("tcp-fast-open", "yes")
        MPVLib.setOptionString("stream-lavf-o", "reconnect=1,reconnect_at_eof=1,reconnect_streamed=1,reconnect_delay_max=2")

        // 🚀 OPTIMIZED: Enhanced Cache settings
        setupOptimizedCache()

        // 🚀 OPTIMIZED: Fast seeking settings
        setupFastSeeking()

        // 🚀 OPTIMIZED: Subtitle settings (from your mpv.conf)
        MPVLib.setOptionString("sub-scale-with-window", "yes")
        MPVLib.setOptionString("sub-use-margins", "no")
        MPVLib.setOptionString("sub-ass-force-margins", "no") // From your config
        MPVLib.setOptionString("sub-font-provider", "none") // Faster rendering
        MPVLib.setOptionString("sub-auto", "fuzzy") // From your config

        // Language preferences (unchanged from original)
        trackSelectionParameters.preferredAudioLanguages.firstOrNull()?.let {
            MPVLib.setOptionString("alang", it.split("-").last())
        }
        trackSelectionParameters.preferredTextLanguages.firstOrNull()?.let {
            println(it.split("-").last())
            MPVLib.setOptionString("slang", it.split("-").last())
        }

        // 🚀 OPTIMIZED: Other performance options (from your mpv.conf)
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("load-scripts", "no") // Disable scripts for performance
        MPVLib.setOptionString("osc", "no") // Disable on-screen controller
        MPVLib.setOptionString("input-default-bindings", "no") // From your config

        MPVLib.init()
        MPVLib.addObserver(this)

        // 🚀 OPTIMIZED: Enhanced property observation
        setupPropertyObservation()

        if (requestAudioFocus) {
            setupAudioFocus()
        }
    }

    // 🚀 OPTIMIZED: Setup enhanced cache (works with your mpv.conf)
    private fun setupOptimizedCache() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB

        // Note: Your mpv.conf already sets cache-default=128000, but we can adjust based on device
        val cacheSize: String
        val demuxerSize: String
        val backBuffer: String

        if (maxMemory >= 512) {
            // High-end device - use your config values or higher
            cacheSize = "128000" // Matches your mpv.conf
            demuxerSize = "128MiB" // Matches your mpv.conf
            backBuffer = "64MiB" // Matches your mpv.conf
        } else if (maxMemory >= 256) {
            // Mid-range device - scale down slightly
            cacheSize = "96000"
            demuxerSize = "96MiB"
            backBuffer = "48MiB"
        } else {
            // Low-end device - use smaller values
            cacheSize = "64000"
            demuxerSize = "64MiB"
            backBuffer = "32MiB"
        }

        Timber.i("🚀 MPV Cache: ${cacheSize}KB, Max Memory: ${maxMemory}MB (complementing mpv.conf)")

        // Apply dynamic cache settings (your mpv.conf handles the base config)
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-default", cacheSize)
        MPVLib.setOptionString("cache-backbuffer", (cacheSize.toInt() / 4).toString())
        MPVLib.setOptionString("cache-seek-min", "8000") // Matches your config
        MPVLib.setOptionString("demuxer-max-bytes", demuxerSize)
        MPVLib.setOptionString("demuxer-max-back-bytes", backBuffer)

        // These match your mpv.conf exactly
        MPVLib.setOptionString("cache-pause", "no")
        MPVLib.setOptionString("cache-pause-initial", "no")
        MPVLib.setOptionString("cache-pause-wait", "1")
        MPVLib.setOptionString("demuxer-readahead-secs", "30")
        MPVLib.setOptionString("cache-secs", "60")

        // Additional buffer optimizations from your config
        MPVLib.setOptionString("demuxer-lavf-buffersize", "524288") // 512KB
        MPVLib.setOptionString("stream-buffer-size", "131072") // 128KB (your config has this too)
    }

    // 🚀 OPTIMIZED: Setup fast seeking (matches your mpv.conf)
    private fun setupFastSeeking() {
        // These match your mpv.conf settings exactly
        MPVLib.setOptionString("hr-seek", "yes")
        MPVLib.setOptionString("hr-seek-framedrop", "yes")
        MPVLib.setOptionString("hr-seek-demuxer-offset", "1.5")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("save-position-on-quit", "no")

        Timber.i("🚀 MPV Fast seeking enabled (complementing mpv.conf)")
    }

    // 🚀 OPTIMIZED: Enhanced property observation - NO DATA CLASS DESTRUCTURING!
    private fun setupPropertyObservation() {
        // Use individual calls instead of data class destructuring
        MPVLib.observeProperty("track-list", MPVLib.MPV_FORMAT_STRING)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("seekable", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_INT64)
        MPVLib.observeProperty("demuxer-cache-time", MPVLib.MPV_FORMAT_INT64)
        MPVLib.observeProperty("speed", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("playlist-current-pos", MPVLib.MPV_FORMAT_INT64)
        // 🚀 OPTIMIZED: Performance monitoring
        MPVLib.observeProperty("cache-speed", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("demuxer-cache-duration", MPVLib.MPV_FORMAT_DOUBLE)
    }

    // 🚀 OPTIMIZED: Setup audio focus
    private fun setupAudioFocus() {
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(this)
            .build()
        val res = audioManager.requestAudioFocus(audioFocusRequest)
        if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            MPVLib.setPropertyBoolean("pause", true)
        }
    }

    // Listeners and notification.
    private val listeners: ListenerSet<Player.Listener> = ListenerSet(
        context.mainLooper,
        Clock.DEFAULT,
    ) { listener: Player.Listener, flags: FlagSet ->
        listener.onEvents(this, Player.Events(flags))
    }
    private val videoListeners =
        CopyOnWriteArraySet<Player.Listener>()

    // Internal state.
    private var internalMediaItems = mutableListOf<MediaItem>()

    @Player.State
    private var playbackState: Int = Player.STATE_IDLE
    private var currentPlayWhenReady: Boolean = false

    @Player.RepeatMode
    private val repeatMode: Int = REPEAT_MODE_OFF
    private var tracks: Tracks = Tracks.EMPTY
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT

    // MPV Custom
    private var isPlayerReady: Boolean = false
    private var isSeekable: Boolean = false
    private var currentPositionMs: Long? = null
    private var currentDurationMs: Long? = null
    private var currentCacheDurationMs: Long? = null
    private var initialCommands = mutableListOf<Array<String>>()
    private var initialIndex: Int = 0
    private var initialSeekTo: Long = 0L

    // mpv events
    override fun eventProperty(property: String) {
        // Nothing to do...
    }

    override fun eventProperty(property: String, value: String) {
        handler.post {
            when (property) {
                "track-list" -> {
                    val newTracks = getTracks(value)
                    tracks = newTracks
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        handler.post {
            when (property) {
                "eof-reached" -> {
                    if (value && isPlayerReady) {
                        if (currentMediaItemIndex < (internalMediaItems.size - 1)) {
                            prepareMediaItem(currentMediaItemIndex + 1)
                        } else {
                            setPlayerStateAndNotifyIfChanged(
                                playWhenReady = false,
                                playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                                playbackState = Player.STATE_ENDED,
                            )
                            resetInternalState()
                        }
                    }
                }
                "paused-for-cache" -> {
                    if (isPlayerReady) {
                        if (value) {
                            setPlayerStateAndNotifyIfChanged(playbackState = Player.STATE_BUFFERING)
                            Timber.d("🚀 Buffering...")
                        } else {
                            setPlayerStateAndNotifyIfChanged(playbackState = Player.STATE_READY)
                            Timber.d("🚀 Ready to play")
                        }
                    }
                }
                "seekable" -> {
                    if (isSeekable != value) {
                        isSeekable = value
                        listeners.sendEvent(Player.EVENT_TIMELINE_CHANGED) { listener ->
                            listener.onTimelineChanged(
                                timeline,
                                Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        handler.post {
            when (property) {
                "time-pos" -> currentPositionMs = value * C.MILLIS_PER_SECOND
                "duration" -> {
                    if (currentDurationMs != value * C.MILLIS_PER_SECOND) {
                        currentDurationMs = value * C.MILLIS_PER_SECOND
                        listeners.sendEvent(Player.EVENT_TIMELINE_CHANGED) { listener ->
                            listener.onTimelineChanged(
                                timeline,
                                Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
                            )
                        }
                    }
                }
                "demuxer-cache-time" -> currentCacheDurationMs = value * C.MILLIS_PER_SECOND
                "playlist-current-pos" -> {
                    currentIndex = value.toInt()
                    if (currentIndex < 0) {
                        return@post
                    }
                    listeners.sendEvent(Player.EVENT_MEDIA_ITEM_TRANSITION) { listener ->
                        listener.onMediaItemTransition(
                            currentMediaItem,
                            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                        )
                    }
                }
            }
        }
    }

    // 🚀 OPTIMIZED: Enhanced property monitoring
    override fun eventProperty(property: String, value: Double) {
        handler.post {
            when (property) {
                "speed" -> {
                    playbackParameters = getPlaybackParameters().withSpeed(value.toFloat())
                    listeners.sendEvent(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED) { listener ->
                        listener.onPlaybackParametersChanged(getPlaybackParameters())
                    }
                }
                "cache-speed" -> {
                    cacheSpeed = value
                    if (value > 0) {
                        Timber.d("🚀 Cache speed: ${value}KB/s")
                    }
                }
                "demuxer-cache-duration" -> {
                    bufferHealth = value
                    Timber.d("🚀 Buffer health: ${value}s")
                }
            }
        }
    }

    override fun event(@MPVLib.Event eventId: Int) {
        handler.post {
            when (eventId) {
                MPVLib.MPV_EVENT_START_FILE -> {
                    Timber.i("🚀 MPV: Starting file...")
                    if (!isPlayerReady) {
                        for (command in initialCommands) {
                            MPVLib.command(command)
                        }
                    }
                }
                MPVLib.MPV_EVENT_SEEK -> {
                    setPlayerStateAndNotifyIfChanged(playbackState = Player.STATE_BUFFERING)
                    listeners.sendEvent(Player.EVENT_POSITION_DISCONTINUITY) { listener ->
                        @Suppress("DEPRECATION")
                        listener.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK)
                    }
                }
                MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                    if (!isPlayerReady) {
                        isPlayerReady = true
                        Timber.i("🚀 MPV: Player ready!")
                        listeners.sendEvent(Player.EVENT_TRACKS_CHANGED) { listener ->
                            listener.onTracksChanged(currentTracks)
                        }
                        seekTo(C.TIME_UNSET)
                        if (playWhenReady) {
                            Timber.d("🚀 Starting playback...")
                            MPVLib.setPropertyBoolean("pause", false)
                        }
                        for (videoListener in videoListeners) {
                            videoListener.onRenderedFirstFrame()
                        }
                    } else {
                        setPlayerStateAndNotifyIfChanged(playbackState = Player.STATE_READY)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun setPlayerStateAndNotifyIfChanged(
        playWhenReady: Boolean = getPlayWhenReady(),
        @Player.PlayWhenReadyChangeReason playWhenReadyChangeReason: Int = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        @Player.State playbackState: Int = getPlaybackState(),
    ) {
        var playerStateChanged = false
        val wasPlaying = isPlaying
        if (playbackState != getPlaybackState()) {
            this.playbackState = playbackState
            listeners.queueEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                listener.onPlaybackStateChanged(playbackState)
            }
            playerStateChanged = true
        }
        if (playWhenReady != getPlayWhenReady()) {
            this.currentPlayWhenReady = playWhenReady
            listeners.queueEvent(Player.EVENT_PLAY_WHEN_READY_CHANGED) { listener ->
                listener.onPlayWhenReadyChanged(playWhenReady, playWhenReadyChangeReason)
            }
            playerStateChanged = true
        }
        if (playerStateChanged) {
            listeners.queueEvent(C.INDEX_UNSET) { listener ->
                listener.onPlaybackStateChanged(playbackState)
            }
        }
        if (wasPlaying != isPlaying) {
            listeners.queueEvent(Player.EVENT_IS_PLAYING_CHANGED) { listener ->
                listener.onIsPlayingChanged(isPlaying)
            }
        }
        listeners.flushEvents()
    }

    private fun selectTrack(
        trackType: TrackType,
        id: String,
    ) {
        MPVLib.setPropertyString(trackType.type, id)
    }

    // Timeline wrapper (unchanged from original)
    private val timeline: Timeline = object : Timeline() {
        override fun getWindowCount(): Int {
            return internalMediaItems.size
        }

        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long,
        ): Window {
            val currentMediaItem =
                internalMediaItems.getOrNull(windowIndex) ?: MediaItem.Builder().build()
            return window.set(
                windowIndex,
                currentMediaItem,
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                isSeekable,
                !isSeekable,
                currentMediaItem.liveConfiguration,
                C.TIME_UNSET,
                Util.msToUs(currentDurationMs ?: C.TIME_UNSET),
                windowIndex,
                windowIndex,
                C.TIME_UNSET,
            )
        }

        override fun getPeriodCount(): Int {
            return internalMediaItems.size
        }

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            return period.set(
                periodIndex,
                periodIndex,
                periodIndex,
                Util.msToUs(currentDurationMs ?: C.TIME_UNSET),
                0,
            )
        }

        override fun getIndexOfPeriod(uid: Any): Int {
            return uid as Int
        }

        override fun getUidOfPeriod(periodIndex: Int): Any {
            return periodIndex
        }
    }

    // OnAudioFocusChangeListener implementation (unchanged from original)
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                -> {
                val oldAudioFocusCallback = audioFocusCallback
                val wasPlaying = isPlaying
                MPVLib.setPropertyBoolean("pause", true)
                setPlayerStateAndNotifyIfChanged(
                    playWhenReady = false,
                    playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                )
                audioFocusCallback = {
                    oldAudioFocusCallback()
                    if (wasPlaying) MPVLib.setPropertyBoolean("pause", false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", "$AUDIO_FOCUS_DUCKING"))
                audioFocusCallback = {
                    MPVLib.command(arrayOf("multiply", "volume", "${1f / AUDIO_FOCUS_DUCKING}"))
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusCallback()
                audioFocusCallback = {}
            }
        }
    }

    // Player implementation (unchanged from original)
    override fun getApplicationLooper(): Looper {
        return handler.looper
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        videoListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        videoListeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        MPVLib.command(arrayOf("playlist-clear"))
        MPVLib.command(arrayOf("playlist-remove", "current"))
        internalMediaItems = mediaItems
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startWindowIndex: Int,
        startPositionMs: Long,
    ) {
        MPVLib.command(arrayOf("playlist-clear"))
        MPVLib.command(arrayOf("playlist-remove", "current"))
        internalMediaItems = mediaItems
        initialIndex = startWindowIndex
        initialSeekTo = startPositionMs / 1000
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        internalMediaItems.addAll(mediaItems)
        mediaItems.forEach { mediaItem ->
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    "${mediaItem.localConfiguration?.uri}",
                    "append",
                ),
            )
        }
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: MutableList<MediaItem>,
    ) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun getAvailableCommands(): Commands {
        return Commands.Builder()
            .addAll(permanentAvailableCommands)
            .addIf(COMMAND_SEEK_TO_DEFAULT_POSITION, !isPlayingAd)
            .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, isCurrentMediaItemSeekable && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem() && !isPlayingAd)
            .addIf(
                COMMAND_SEEK_TO_PREVIOUS,
                !currentTimeline.isEmpty &&
                        (hasPreviousMediaItem() || !isCurrentMediaItemLive || isCurrentMediaItemSeekable) &&
                        !isPlayingAd,
            )
            .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem() && !isPlayingAd)
            .addIf(
                COMMAND_SEEK_TO_NEXT,
                !currentTimeline.isEmpty &&
                        (hasNextMediaItem() || (isCurrentMediaItemLive && isCurrentMediaItemDynamic)) &&
                        !isPlayingAd,
            )
            .addIf(COMMAND_SEEK_TO_MEDIA_ITEM, !isPlayingAd)
            .addIf(COMMAND_SEEK_BACK, isCurrentMediaItemSeekable && !isPlayingAd)
            .addIf(COMMAND_SEEK_FORWARD, isCurrentMediaItemSeekable && !isPlayingAd)
            .build()
    }

    private fun resetInternalState() {
        isPlayerReady = false
        isSeekable = false
        playbackState = Player.STATE_IDLE
        currentPlayWhenReady = false
        currentPositionMs = null
        currentDurationMs = null
        currentCacheDurationMs = null
        tracks = Tracks.EMPTY
        playbackParameters = PlaybackParameters.DEFAULT
        initialCommands.clear()
    }

    override fun prepare() {
        internalMediaItems.forEachIndexed { index, mediaItem ->
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    "${mediaItem.localConfiguration?.uri}",
                    if (index == 0) "replace" else "append",
                ),
            )
        }
        prepareMediaItem(initialIndex)
    }

    override fun getPlaybackState(): Int {
        return playbackState
    }

    override fun getPlaybackSuppressionReason(): Int {
        return PLAYBACK_SUPPRESSION_REASON_NONE
    }

    override fun getPlayerError(): PlaybackException? {
        return null
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (currentPlayWhenReady != playWhenReady) {
            setPlayerStateAndNotifyIfChanged(
                playWhenReady = playWhenReady,
                playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            if (isPlayerReady) {
                if (requestAudioFocus && playWhenReady) {
                    val res = audioManager.requestAudioFocus(audioFocusRequest)
                    if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        MPVLib.setPropertyBoolean("pause", true)
                    } else {
                        MPVLib.setPropertyBoolean("pause", false)
                    }
                } else {
                    MPVLib.setPropertyBoolean("pause", !playWhenReady)
                }
            }
        }
    }

    override fun getPlayWhenReady(): Boolean {
        return currentPlayWhenReady
    }

    override fun setRepeatMode(repeatMode: Int) {
        when (repeatMode) {
            REPEAT_MODE_OFF -> {
                MPVLib.setOptionString("loop-file", "no")
                MPVLib.setOptionString("loop-playlist", "no")
            }
            REPEAT_MODE_ONE -> {
                MPVLib.setOptionString("loop-file", "inf")
                MPVLib.setOptionString("loop-playlist", "no")
            }
            REPEAT_MODE_ALL -> {
                MPVLib.setOptionString("loop-file", "no")
                MPVLib.setOptionString("loop-playlist", "inf")
            }
        }
    }

    override fun getRepeatMode(): Int {
        return repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getShuffleModeEnabled(): Boolean {
        return false
    }

    override fun isLoading(): Boolean {
        return false
    }

    // 🚀 OPTIMIZED: Enhanced seeking with performance logging
    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        @Player.Command seekCommand: Int,
        isRepeatingCurrentItem: Boolean,
    ) {
        val startTime = System.currentTimeMillis()

        if (mediaItemIndex == currentMediaItemIndex) {
            val seekTo =
                if (positionMs != C.TIME_UNSET) positionMs / C.MILLIS_PER_SECOND else initialSeekTo
            initialSeekTo = if (isPlayerReady) {
                MPVLib.command(arrayOf("seek", "$seekTo", "absolute"))
                val seekTime = System.currentTimeMillis() - startTime
                Timber.d("🚀 Seek to ${seekTo}s completed in ${seekTime}ms")
                0L
            } else {
                seekTo
            }
        } else {
            prepareMediaItem(mediaItemIndex)
            play()
        }
    }

    private fun prepareMediaItem(index: Int) {
        internalMediaItems.getOrNull(index)?.let { mediaItem ->
            resetInternalState()
            mediaItem.localConfiguration?.subtitleConfigurations?.forEach { subtitle ->
                initialCommands.add(
                    arrayOf(
                        "sub-add",
                        "${subtitle.uri}",
                        "auto",
                        "${subtitle.label}",
                        "${subtitle.language}",
                    ),
                )
            }
            if (currentMediaItemIndex != index) {
                MPVLib.command(arrayOf("playlist-play-index", "$index"))
            }
            listeners.sendEvent(Player.EVENT_TIMELINE_CHANGED) { listener ->
                listener.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            }
            setPlayerStateAndNotifyIfChanged(playbackState = Player.STATE_BUFFERING)
        }
    }

    override fun getSeekBackIncrement(): Long {
        return seekBackIncrement
    }

    override fun getSeekForwardIncrement(): Long {
        return seekForwardIncrement
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (getPlaybackParameters().speed != playbackParameters.speed) {
            MPVLib.setPropertyDouble("speed", playbackParameters.speed.toDouble())
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return playbackParameters
    }

    override fun stop() {
        MPVLib.command(arrayOf("stop", "keep-playlist"))
    }

    override fun release() {
        if (requestAudioFocus) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
        resetInternalState()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun getCurrentTracks(): Tracks {
        return tracks
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return trackSelectionParameters
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        trackSelectionParameters = parameters

        val disabledTrackTypes = parameters.disabledTrackTypes.map { TrackType.fromMedia3TrackType(it) }

        val notOverriddenTypes = mutableSetOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.SUBTITLE)
        for (override in parameters.overrides) {
            val trackType = TrackType.fromMedia3TrackType(override.key.type)
            notOverriddenTypes.remove(trackType)
            val id = override.key.getFormat(0).id ?: continue

            selectTrack(trackType, id)
        }
        for (notOverriddenType in notOverriddenTypes) {
            if (notOverriddenType in disabledTrackTypes) {
                selectTrack(notOverriddenType, "no")
            } else {
                selectTrack(notOverriddenType, "auto")
            }
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        return MediaMetadata.EMPTY
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return MediaMetadata.EMPTY
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        TODO("Not yet implemented")
    }

    override fun getCurrentTimeline(): Timeline {
        return timeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return currentMediaItemIndex
    }

    override fun getCurrentMediaItemIndex(): Int {
        return currentIndex
    }

    override fun getDuration(): Long {
        return timeline.getWindow(currentMediaItemIndex, window).durationMs
    }

    override fun getCurrentPosition(): Long {
        return currentPositionMs ?: C.TIME_UNSET
    }

    override fun getBufferedPosition(): Long {
        return currentCacheDurationMs ?: contentPosition
    }

    override fun getTotalBufferedDuration(): Long {
        return bufferedPosition
    }

    override fun isPlayingAd(): Boolean {
        return false
    }

    override fun getCurrentAdGroupIndex(): Int {
        return C.INDEX_UNSET
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return C.INDEX_UNSET
    }

    override fun getContentPosition(): Long {
        return currentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return bufferedPosition
    }

    override fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.DEFAULT
    }

    override fun setVolume(audioVolume: Float) {
        TODO("Not yet implemented")
    }

    override fun getVolume(): Float {
        return MPVLib.getPropertyInt("volume") / 100F
    }

    override fun clearVideoSurface() {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        surfaceView?.holder?.addCallback(surfaceHolder)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        surfaceView?.holder?.removeCallback(surfaceHolder)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun getVideoSize(): VideoSize {
        val width = MPVLib.getPropertyInt("width")
        val height = MPVLib.getPropertyInt("height")
        if (width == null || height == null) return VideoSize.UNKNOWN
        return VideoSize(width, height)
    }

    override fun getSurfaceSize(): Size {
        val mpvSize = MPVLib.getPropertyString("android-surface-size").split("x")
        return try {
            Size(mpvSize[0].toInt(), mpvSize[1].toInt())
        } catch (_: IndexOutOfBoundsException) {
            Size.UNKNOWN
        }
    }

    override fun getCurrentCues(): CueGroup {
        return CueGroup(emptyList(), 0)
    }

    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
            .setMaxVolume(0)
            .setMaxVolume(100)
            .build()
    }

    override fun getDeviceVolume(): Int {
        return MPVLib.getPropertyInt("volume")
    }

    override fun isDeviceMuted(): Boolean {
        return MPVLib.getPropertyBoolean("mute")
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        throw IllegalArgumentException("You should use global volume controls. Check out AUDIO_SERVICE.")
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        MPVLib.setPropertyInt("volume", volume)
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        throw IllegalArgumentException("You should use global volume controls. Check out AUDIO_SERVICE.")
    }

    override fun increaseDeviceVolume(flags: Int) {
        throw IllegalArgumentException("You should use global volume controls. Check out AUDIO_SERVICE.")
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        throw IllegalArgumentException("You should use global volume controls. Check out AUDIO_SERVICE.")
    }

    override fun decreaseDeviceVolume(flags: Int) {
        throw IllegalArgumentException("You should use global volume controls. Check out AUDIO_SERVICE.")
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        throw IllegalArgumentException("You should use global volume controls. Check out AUDIO_SERVICE.")
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        return MPVLib.setPropertyBoolean("mute", muted)
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        TODO("Not yet implemented")
    }

    fun updateZoomMode(enabled: Boolean) {
        if (enabled) {
            MPVLib.setOptionString("panscan", "1")
            MPVLib.setOptionString("sub-use-margins", "yes")
            MPVLib.setOptionString("sub-ass-force-margins", "yes")
        } else {
            MPVLib.setOptionString("panscan", "0")
            MPVLib.setOptionString("sub-use-margins", "no")
            MPVLib.setOptionString("sub-ass-force-margins", "no")
        }
    }

    // 🚀 OPTIMIZED: Get performance statistics
    fun getPerformanceStats(): Map<String, Any> {
        val cacheUsed = MPVLib.getPropertyInt("cache-used")
        Timber.d("🚀 cache-used: $cacheUsed, type: ${cacheUsed?.javaClass}")
        return mapOf(
            "cache_speed" to "${cacheSpeed}KB/s",
            "buffer_health" to "${bufferHealth}s",
            "hwdec_active" to MPVLib.getPropertyBoolean("hwdec-current"),
            "cache_used" to (cacheUsed ?: 0),
            "demuxer_cache_time" to (currentCacheDurationMs ?: 0) / 1000
        )
    }

    private val surfaceHolder: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            MPVLib.attachSurface(holder.surface)
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setOptionString("vo", videoOutput)
            MPVLib.setOptionString("vid", "auto")
            Timber.i("🚀 MPV surface created")
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
            Timber.d("🚀 MPV surface: ${width}x$height")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            MPVLib.setOptionString("vid", "no")
            MPVLib.setOptionString("vo", "null")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
            Timber.i("🚀 MPV surface destroyed")
        }
    }

    companion object {
        private const val AUDIO_FOCUS_DUCKING = 0.5f

        private val permanentAvailableCommands: Commands = Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_SET_SPEED_AND_PITCH,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_CHANGE_MEDIA_ITEMS,
                COMMAND_SET_VIDEO_SURFACE,
                COMMAND_GET_TRACKS,
                COMMAND_SET_TRACK_SELECTION_PARAMETERS,
            )
            .build()

        private fun JSONObject.optNullableString(name: String): String? {
            return if (this.has(name) && !this.isNull(name)) {
                this.getString(name)
            } else {
                null
            }
        }

        private fun JSONObject.optNullableDouble(name: String): Double? {
            return if (this.has(name) && !this.isNull(name)) {
                this.getDouble(name)
            } else {
                null
            }
        }

        private fun createTracksGroupfromMpvJson(json: JSONObject): Tracks.Group {
            val trackType = TrackType.entries.first { it.type == json.optString("type") }

            val baseFormat = Format.Builder()
                .setId(json.optInt("id"))
                .setLabel(json.optNullableString("title"))
                .setLanguage(json.optNullableString("lang"))
                .setSelectionFlags(if (json.optBoolean("default")) C.SELECTION_FLAG_DEFAULT else 0)
                .setCodecs(json.optNullableString("codec"))
                .build()

            val format = when (trackType) {
                TrackType.VIDEO -> {
                    baseFormat.buildUpon()
                        .setSampleMimeType("video/${baseFormat.codecs}")
                        .setWidth(json.optInt("demux-w", Format.NO_VALUE))
                        .setHeight(json.optInt("demux-h", Format.NO_VALUE))
                        .setFrameRate(
                            (json.optNullableDouble("demux-w") ?: Format.NO_VALUE).toFloat(),
                        )
                        .build()
                }

                TrackType.AUDIO -> {
                    baseFormat.buildUpon()
                        .setSampleMimeType("audio/${baseFormat.codecs}")
                        .setChannelCount(json.optInt("demux-channel-count", Format.NO_VALUE))
                        .setSampleRate(json.optInt("demux-samplerate", Format.NO_VALUE))
                        .build()
                }

                TrackType.SUBTITLE -> {
                    baseFormat.buildUpon()
                        .setSampleMimeType("text/${baseFormat.codecs}")
                        .build()
                }
            }

            val trackGroup = TrackGroup(format)

            return Tracks.Group(
                trackGroup,
                false,
                IntArray(trackGroup.length) { C.FORMAT_HANDLED },
                BooleanArray(trackGroup.length) { json.optBoolean("selected") },
            )
        }

        private fun getTracks(trackList: String): Tracks {
            var tracks = Tracks.EMPTY
            val trackGroups = mutableListOf<Tracks.Group>()
            try {
                val currentTrackList = JSONArray(trackList)
                for (index in 0 until currentTrackList.length()) {
                    val tracksGroup = createTracksGroupfromMpvJson(currentTrackList.getJSONObject(index))
                    trackGroups.add(tracksGroup)
                }
                if (trackGroups.isNotEmpty()) {
                    tracks = Tracks(trackGroups)
                }
            } catch (_: JSONException) {
            }
            return tracks
        }
    }
}
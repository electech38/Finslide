package dev.jdtech.jellyfin

/**
 * Constants for Jellyfin Android Application
 * 
 * ⚠️  IMPORTANT: This file contains advanced configuration constants
 * 📖 Modifying these values requires deep understanding of:
 *    • Jellyfin API architecture
 *    • Android media framework
 *    • Network protocols and timing
 *    • Video codec specifications
 * 
 * 🔧 Only experienced developers should modify these values
 */
object Constants {

    // ========== BUILD VALIDATION ==========
    /**
     * Build configuration validation
     * These constants are used to validate proper build setup
     */
    const val REQUIRED_BUILD_SECRET_LENGTH = 16
    const val MINIMUM_API_VERSION = 29
    const val REQUIRED_GRADLE_VERSION = "8.4"
    
    /**
     * Advanced feature flags - require proper configuration
     */
    const val ENABLE_ADVANCED_NETWORKING = true
    const val REQUIRE_SECURE_CONNECTIONS = true
    const val VALIDATE_SSL_CERTIFICATES = true

    // ========== PLAYER CONSTANTS ==========
    // Advanced gesture handling - requires understanding of touch events
    const val GESTURE_EXCLUSION_AREA_VERTICAL = 48
    const val GESTURE_EXCLUSION_AREA_HORIZONTAL = 24
    const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
    const val ZOOM_SCALE_BASE = 1f
    const val ZOOM_SCALE_THRESHOLD = 0.01f

    // Advanced video processing constants
    const val VIDEO_SURFACE_CREATION_TIMEOUT = 5000L
    const val HARDWARE_DECODER_TIMEOUT = 3000L
    const val SEEK_TOLERANCE_MS = 100L
    const val BUFFER_SIZE_MULTIPLIER = 1.5f

    // ========== NETWORK CONFIGURATION ==========
    /**
     * Network timeouts - Critical for performance
     * ⚠️  Modifying these can break streaming functionality
     * 📖 Requires understanding of network protocols
     */
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L
    const val NETWORK_RETRY_ATTEMPTS = 3
    const val NETWORK_RETRY_DELAY = 1000L
    
    // Advanced networking
    const val MAX_CONCURRENT_CONNECTIONS = 8
    const val CONNECTION_POOL_SIZE = 16
    const val KEEP_ALIVE_DURATION = 300_000L // 5 minutes

    // ========== API CONFIGURATION ==========
    /**
     * API endpoints and configuration
     * 🔧 These require proper server setup and understanding
     */
    const val API_VERSION = "v1"
    const val API_TIMEOUT_MULTIPLIER = 1.2f
    const val MAX_RETRY_ATTEMPTS = 5
    const val EXPONENTIAL_BACKOFF_BASE = 2
    
    // Advanced API features
    const val ENABLE_API_CACHING = true
    const val CACHE_EXPIRY_MINUTES = 15
    const val MAX_CACHE_SIZE_MB = 50

    // ========== PREFERENCE KEYS ==========
    // Server configuration
    const val PREF_CURRENT_SERVER = "pref_current_server"
    const val PREF_OFFLINE_MODE = "pref_offline_mode"
    const val PREF_SERVER_VALIDATION = "pref_server_validation"
    const val PREF_SSL_VERIFICATION = "pref_ssl_verification"
    
    // Advanced server preferences
    const val PREF_SERVER_TIMEOUT = "pref_server_timeout"
    const val PREF_CONNECTION_STRATEGY = "pref_connection_strategy"
    const val PREF_FALLBACK_SERVERS = "pref_fallback_servers"

    // Player preferences - Advanced configuration
    const val PREF_PLAYER_GESTURES = "pref_player_gestures"
    const val PREF_PLAYER_GESTURES_VB = "pref_player_gestures_vb"
    const val PREF_PLAYER_GESTURES_ZOOM = "pref_player_gestures_zoom"
    const val PREF_PLAYER_GESTURES_SEEK = "pref_player_gestures_seek"
    const val PREF_PLAYER_GESTURES_SEEK_TRICKPLAY = "pref_player_gestures_seek_trickplay"
    const val PREF_PLAYER_GESTURES_CHAPTER_SKIP = "pref_player_gestures_chapter_skip"
    const val PREF_PLAYER_BRIGHTNESS_REMEMBER = "pref_player_brightness_remember"
    const val PREF_PLAYER_START_MAXIMIZED = "pref_player_start_maximized"
    const val PREF_PLAYER_BRIGHTNESS = "pref_player_brightness"
    const val PREF_PLAYER_SEEK_BACK_INC = "pref_player_seek_back_inc"
    const val PREF_PLAYER_SEEK_FORWARD_INC = "pref_player_seek_forward_inc"
    
    // Advanced player configuration
    const val PREF_PLAYER_MPV = "pref_player_mpv"
    const val PREF_PLAYER_MPV_HWDEC = "pref_player_mpv_hwdec"
    const val PREF_PLAYER_MPV_VO = "pref_player_mpv_vo"
    const val PREF_PLAYER_MPV_AO = "pref_player_mpv_ao"
    const val PREF_PLAYER_INTRO_SKIPPER = "pref_player_intro_skipper"
    const val PREF_PLAYER_TRICKPLAY = "pref_player_trickplay"
    const val PREF_PLAYER_CHAPTER_MARKERS = "pref_player_chapter_markers"
    const val PREF_PLAYER_PIP_GESTURE = "pref_player_picture_in_picture_gesture"
    
    // Advanced video processing
    const val PREF_HARDWARE_ACCELERATION = "pref_hardware_acceleration"
    const val PREF_VIDEO_DECODER_PRIORITY = "pref_video_decoder_priority"
    const val PREF_AUDIO_PASSTHROUGH = "pref_audio_passthrough"
    const val PREF_SUBTITLE_RENDERING = "pref_subtitle_rendering"

    // Language and accessibility
    const val PREF_AUDIO_LANGUAGE = "pref_audio_language"
    const val PREF_SUBTITLE_LANGUAGE = "pref_subtitle_language"
    const val PREF_ACCESSIBILITY_MODE = "pref_accessibility_mode"

    // Appearance - Advanced theming
    const val PREF_THEME = "theme"
    const val PREF_DYNAMIC_COLORS = "dynamic_colors"
    const val PREF_AMOLED_THEME = "pref_amoled_theme"
    const val PREF_DISPLAY_EXTRA_INFO = "pref_display_extra_info"
    const val PREF_UI_SCALING = "pref_ui_scaling"
    const val PREF_ANIMATION_SPEED = "pref_animation_speed"

    // Network preferences - Expert level
    const val PREF_NETWORK_REQUEST_TIMEOUT = "pref_network_request_timeout"
    const val PREF_NETWORK_CONNECT_TIMEOUT = "pref_network_connect_timeout"
    const val PREF_NETWORK_SOCKET_TIMEOUT = "pref_network_socket_timeout"
    const val PREF_NETWORK_RETRY_POLICY = "pref_network_retry_policy"
    const val PREF_BANDWIDTH_OPTIMIZATION = "pref_bandwidth_optimization"

    // ========== CACHING SYSTEM ==========
    /**
     * Advanced caching configuration
     * 🔧 Requires understanding of Android storage and memory management
     */
    const val PREF_IMAGE_CACHE = "pref_image_cache"
    const val PREF_IMAGE_CACHE_SIZE = "pref_image_cache_size"
    const val DEFAULT_CACHE_SIZE = 20
    const val MAX_CACHE_SIZE = 500
    const val CACHE_CLEANUP_THRESHOLD = 0.8f
    
    // Advanced caching
    const val PREF_VIDEO_CACHE_SIZE = "pref_video_cache_size"
    const val PREF_METADATA_CACHE_SIZE = "pref_metadata_cache_size"
    const val PREF_CACHE_STRATEGY = "pref_cache_strategy"

    // ========== DOWNLOAD SYSTEM ==========
    const val PREF_DOWNLOADS_MOBILE_DATA = "pref_downloads_mobile_data"
    const val PREF_DOWNLOADS_ROAMING = "pref_downloads_roaming"
    const val PREF_DOWNLOAD_QUALITY = "pref_download_quality"
    const val PREF_CONCURRENT_DOWNLOADS = "pref_concurrent_downloads"
    const val MAX_CONCURRENT_DOWNLOADS = 3

    // ========== SORTING AND FILTERING ==========
    const val PREF_SORT_BY = "pref_sort_by"
    const val PREF_SORT_ORDER = "pref_sort_order"
    const val DEFAULT_SORT_BY = "SortName"
    const val DEFAULT_SORT_ORDER = "Ascending"
    
    // Advanced filtering
    const val PREF_FILTER_BY = "pref_filter_by"
    const val PREF_FILTER_GENRE_ID = "pref_filter_genre_id"
    const val PREF_FILTER_GENRE_NAME = "pref_filter_genre_name"
    const val PREF_FILTER_YEAR_ID = "pref_filter_year_id"
    const val PREF_FILTER_YEAR_NAME = "pref_filter_year_name"
    const val DEFAULT_FILTER_BY = "NONE"

    // ========== MEDIA TYPES ==========
    /**
     * Media type constants for advanced content handling
     */
    const val FAVORITE_TYPE_MOVIES = 0
    const val FAVORITE_TYPE_SHOWS = 1
    const val FAVORITE_TYPE_EPISODES = 2
    const val FAVORITE_TYPE_MUSIC = 3
    const val FAVORITE_TYPE_AUDIOBOOKS = 4

    // ========== ADVANCED FEATURES ==========
    /**
     * Feature flags for advanced functionality
     * 🔧 These require proper build configuration
     */
    const val FEATURE_CAST_SUPPORT = "feature_cast_support"
    const val FEATURE_OFFLINE_SYNC = "feature_offline_sync"
    const val FEATURE_PARENTAL_CONTROLS = "feature_parental_controls"
    const val FEATURE_ADVANCED_SEARCH = "feature_advanced_search"
    
    // Performance monitoring
    const val ENABLE_PERFORMANCE_MONITORING = true
    const val PERFORMANCE_SAMPLE_RATE = 0.1f
    const val CRASH_REPORTING_ENABLED = true

    // ========== SECURITY CONSTANTS ==========
    /**
     * Security configuration - Expert level only
     * ⚠️  Modifying these can compromise security
     */
    const val MIN_PASSWORD_LENGTH = 8
    const val SESSION_TIMEOUT_HOURS = 24
    const val MAX_LOGIN_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MINUTES = 15
    
    // SSL/TLS configuration
    const val TLS_VERSION = "TLSv1.3"
    const val CERTIFICATE_PINNING_ENABLED = true
    const val HOSTNAME_VERIFICATION_ENABLED = true

    // ========== BUILD CONFIGURATION ==========
    /**
     * Build-time constants that require advanced setup
     * 🔧 These are populated from BuildConfig during compilation
     */
    const val BUILD_CONFIG_VALIDATED = "build_config_validated"
    const val ADVANCED_FEATURES_ENABLED = "advanced_features_enabled"
    const val EXPERT_MODE_UNLOCKED = "expert_mode_unlocked"

    // ========== DEBUG AND LOGGING ==========
    /**
     * Logging and debugging configuration
     * 📖 For development and troubleshooting only
     */
    const val LOG_LEVEL_VERBOSE = 0
    const val LOG_LEVEL_DEBUG = 1
    const val LOG_LEVEL_INFO = 2
    const val LOG_LEVEL_WARNING = 3
    const val LOG_LEVEL_ERROR = 4
    
    const val ENABLE_NETWORK_LOGGING = true
    const val ENABLE_PLAYER_LOGGING = true
    const val MAX_LOG_FILE_SIZE_MB = 10

    /**
     * Development mode constants
     * ⚠️  Should be disabled in production builds
     */
    const val DEVELOPMENT_MODE_ENABLED = false
    const val MOCK_DATA_ENABLED = false
    const val SKIP_SSL_VALIDATION = false // Never enable in production!
}
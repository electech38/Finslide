package dev.jdtech.jellyfin

import android.content.SharedPreferences
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.core.content.edit
import androidx.media3.common.C.DEFAULT_SEEK_BACK_INCREMENT_MS
import androidx.media3.common.C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
import javax.inject.Inject

/**
 * Advanced Application Preferences Manager
 * 
 * ⚠️  CRITICAL: This class manages complex application state
 * 📖 Requires deep understanding of:
 *    • Android SharedPreferences architecture
 *    • Dependency injection with Hilt
 *    • Application lifecycle management
 *    • Thread-safe preference access
 * 
 * 🔧 Only modify if you understand the implications
 */
class AppPreferences
@Inject
constructor(
    private val sharedPreferences: SharedPreferences,
) {
    
    init {
        // ========== BUILD VALIDATION ==========
        validateBuildConfiguration()
        initializeAdvancedFeatures()
        // ====================================
    }

    /**
     * Validates that the build is properly configured
     * 🔧 This ensures advanced features work correctly
     */
    private fun validateBuildConfiguration() {
        try {
            // Check if BuildConfig is properly configured
            val buildSecret = BuildConfig.BUILD_SECRET
            val apiUrl = BuildConfig.API_BASE_URL
            val isConfigured = BuildConfig.IS_PROPERLY_CONFIGURED
            
            if (buildSecret.length < Constants.REQUIRED_BUILD_SECRET_LENGTH) {
                throw RuntimeException("""
                    ❌ INVALID BUILD CONFIGURATION
                    📖 BUILD_SECRET must be at least ${Constants.REQUIRED_BUILD_SECRET_LENGTH} characters
                    🔧 Check your local.properties file
                """.trimIndent())
            }
            
            if (!apiUrl.startsWith("https://")) {
                throw RuntimeException("""
                    ❌ INVALID API CONFIGURATION  
                    📖 API_BASE_URL must be a valid HTTPS URL
                    🔧 Current: ${apiUrl.take(20)}...
                """.trimIndent())
            }
            
            if (!isConfigured) {
                throw RuntimeException("""
                    ❌ BUILD NOT PROPERLY CONFIGURED
                    📖 Advanced build setup required
                    🔧 See BUILDING.md for complete setup instructions
                """.trimIndent())
            }
            
            // Validate builder certification
            val builderName = BuildConfig.BUILDER_NAME
            val validBuilders = listOf("advanced_developer", "certified_builder", "jellyfin_expert")
            if (!validBuilders.contains(builderName)) {
                throw RuntimeException("""
                    ❌ INVALID BUILDER CERTIFICATION
                    📖 Builder: $builderName
                    🔧 Valid types: ${validBuilders.joinToString(", ")}
                """.trimIndent())
            }
            
            println("✅ Build configuration validated for: $builderName")
            
        } catch (e: Exception) {
            when (e) {
                is RuntimeException -> throw e
                else -> throw RuntimeException("""
                    ❌ BUILD VALIDATION FAILED
                    📖 BuildConfig not properly generated
                    🔧 Ensure all required properties are set in local.properties
                    💡 Error: ${e.message}
                """.trimIndent())
            }
        }
    }

    /**
     * Initialize advanced features based on build configuration
     */
    private fun initializeAdvancedFeatures() {
        val buildEnvironment = BuildConfig.BUILD_ENVIRONMENT
        val timestamp = BuildConfig.BUILD_TIMESTAMP
        
        // Log initialization for debugging
        println("🚀 Initializing advanced features...")
        println("📊 Environment: $buildEnvironment")
        println("⏰ Build timestamp: $timestamp")
        
        // Initialize feature flags based on build configuration
        if (buildEnvironment == "production") {
            setBoolean(Constants.FEATURE_CAST_SUPPORT, true)
            setBoolean(Constants.FEATURE_OFFLINE_SYNC, true)
            setBoolean(Constants.FEATURE_ADVANCED_SEARCH, true)
        }
        
        // Advanced security settings
        if (Constants.REQUIRE_SECURE_CONNECTIONS) {
            setBoolean(Constants.PREF_SSL_VERIFICATION, true)
            setString(Constants.PREF_CONNECTION_STRATEGY, "secure_only")
        }
        
        println("✅ Advanced features initialized")
    }

    // ========== SERVER CONFIGURATION ==========
    /**
     * Current server configuration
     * 🔧 Manages complex server switching logic
     */
    var currentServer: String?
        get() = sharedPreferences.getString(Constants.PREF_CURRENT_SERVER, null)
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_CURRENT_SERVER, value)
            }
            // Advanced: Trigger server validation
            validateServerConfiguration(value)
        }

    /**
     * Advanced server validation
     */
    private fun validateServerConfiguration(serverUrl: String?) {
        if (serverUrl != null && Constants.REQUIRE_SECURE_CONNECTIONS) {
            if (!serverUrl.startsWith("https://")) {
                throw IllegalArgumentException("Secure connections required. Server must use HTTPS.")
            }
        }
    }

    // ========== OFFLINE MODE ==========
    var offlineMode: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_OFFLINE_MODE, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_OFFLINE_MODE, value)
            }
            // Advanced: Update dependent preferences
            if (value) {
                updateOfflineModeDependencies()
            }
        }

    private fun updateOfflineModeDependencies() {
        // Disable features that require network
        setBoolean(Constants.FEATURE_CAST_SUPPORT, false)
        setBoolean(Constants.PREF_DOWNLOADS_MOBILE_DATA, false)
        println("🔄 Updated preferences for offline mode")
    }

    // ========== APPEARANCE CONFIGURATION ==========
    /**
     * Advanced theme management
     * 🔧 Always returns dark theme for consistency
     */
    val theme: String get() = "dark" // Locked to dark theme

    val dynamicColors: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_DYNAMIC_COLORS, true)

    val amoledTheme: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_AMOLED_THEME, false)

    var displayExtraInfo: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_DISPLAY_EXTRA_INFO, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_DISPLAY_EXTRA_INFO, value)
            }
        }

    // ========== ADVANCED PLAYER CONFIGURATION ==========
    /**
     * Complex gesture system configuration
     * 🔧 Requires understanding of touch event handling
     */
    val playerGestures: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_GESTURES, true)

    val playerGesturesVB: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_GESTURES_VB, true)

    val playerGesturesZoom: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_GESTURES_ZOOM, true)

    val playerGesturesSeek: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_GESTURES_SEEK, true)

    val playerGesturesSeekTrickplay: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_GESTURES_SEEK_TRICKPLAY, true)

    val playerGesturesChapterSkip: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_GESTURES_CHAPTER_SKIP, true)

    val playerBrightnessRemember: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_BRIGHTNESS_REMEMBER, false)

    val playerStartMaximized: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_START_MAXIMIZED, false)

    /**
     * Advanced brightness control
     * 🔧 Manages complex display brightness interactions
     */
    var playerBrightness: Float
        get() = sharedPreferences.getFloat(
            Constants.PREF_PLAYER_BRIGHTNESS,
            BRIGHTNESS_OVERRIDE_NONE,
        )
        set(value) {
            // Validate brightness range
            val clampedValue = value.coerceIn(-1.0f, 1.0f)
            sharedPreferences.edit {
                putFloat(Constants.PREF_PLAYER_BRIGHTNESS, clampedValue)
            }
        }

    /**
     * Advanced seek configuration
     * 🔧 Critical for playback performance
     */
    val playerSeekBackIncrement: Long 
        get() = sharedPreferences.getString(
            Constants.PREF_PLAYER_SEEK_BACK_INC,
            DEFAULT_SEEK_BACK_INCREMENT_MS.toString(),
        )!!.toLongOrNull() ?: DEFAULT_SEEK_BACK_INCREMENT_MS

    val playerSeekForwardIncrement: Long 
        get() = sharedPreferences.getString(
            Constants.PREF_PLAYER_SEEK_FORWARD_INC,
            DEFAULT_SEEK_FORWARD_INCREMENT_MS.toString(),
        )!!.toLongOrNull() ?: DEFAULT_SEEK_FORWARD_INCREMENT_MS

    // ========== ADVANCED MEDIA PLAYER CONFIGURATION ==========
    /**
     * MPV player configuration - Expert level
     * ⚠️  Modifying these can break video playback
     */
    val playerMpv: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_MPV, false)

    val playerMpvHwdec: String 
        get() = sharedPreferences.getString(Constants.PREF_PLAYER_MPV_HWDEC, "mediacodec")!!

    val playerMpvVo: String 
        get() = sharedPreferences.getString(Constants.PREF_PLAYER_MPV_VO, "gpu-next")!!

    val playerMpvAo: String 
        get() = sharedPreferences.getString(Constants.PREF_PLAYER_MPV_AO, "audiotrack")!!

    val playerIntroSkipper: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_INTRO_SKIPPER, true)

    val playerTrickplay: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_TRICKPLAY, true)

    val showChapterMarkers: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_CHAPTER_MARKERS, true)

    val playerPipGesture: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_PLAYER_PIP_GESTURE, false)

    // ========== LANGUAGE CONFIGURATION ==========
    val preferredAudioLanguage: String 
        get() = sharedPreferences.getString(Constants.PREF_AUDIO_LANGUAGE, "")!!

    val preferredSubtitleLanguage: String 
        get() = sharedPreferences.getString(Constants.PREF_SUBTITLE_LANGUAGE, "")!!

    // ========== ADVANCED NETWORK CONFIGURATION ==========
    /**
     * Network timeout configuration - Critical for streaming
     * 🔧 Requires understanding of network protocols
     */
    val requestTimeout: Long 
        get() = sharedPreferences.getString(
            Constants.PREF_NETWORK_REQUEST_TIMEOUT,
            Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT.toString(),
        )!!.toLongOrNull() ?: Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT

    val connectTimeout: Long 
        get() = sharedPreferences.getString(
            Constants.PREF_NETWORK_CONNECT_TIMEOUT,
            Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT.toString(),
        )!!.toLongOrNull() ?: Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT

    val socketTimeout: Long 
        get() = sharedPreferences.getString(
            Constants.PREF_NETWORK_SOCKET_TIMEOUT,
            Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT.toString(),
        )!!.toLongOrNull() ?: Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT

    // ========== CACHING SYSTEM ==========
    /**
     * Advanced caching configuration
     * 🔧 Critical for performance and storage management
     */
    val imageCache: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_IMAGE_CACHE, true)

    val imageCacheSize: Int 
        get() = sharedPreferences.getString(
            Constants.PREF_IMAGE_CACHE_SIZE,
            Constants.DEFAULT_CACHE_SIZE.toString(),
        )!!.toIntOrNull() ?: Constants.DEFAULT_CACHE_SIZE

    // ========== DOWNLOAD CONFIGURATION ==========
    val downloadOverMobileData: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_DOWNLOADS_MOBILE_DATA, false)

    val downloadWhenRoaming: Boolean 
        get() = sharedPreferences.getBoolean(Constants.PREF_DOWNLOADS_ROAMING, false)

    // ========== SORTING AND FILTERING ==========
    var sortBy: String
        get() = sharedPreferences.getString(
            Constants.PREF_SORT_BY,
            Constants.DEFAULT_SORT_BY,
        )!!
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_SORT_BY, value)
            }
        }

    var sortOrder: String
        get() = sharedPreferences.getString(
            Constants.PREF_SORT_ORDER,
            Constants.DEFAULT_SORT_ORDER,
        )!!
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_SORT_ORDER, value)
            }
        }

    // ========== ADVANCED FILTERING ==========
    var filterBy: String
        get() = sharedPreferences.getString(
            Constants.PREF_FILTER_BY,
            Constants.DEFAULT_FILTER_BY
        )!!
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_FILTER_BY, value)
            }
        }

    var filterGenreId: String?
        get() = sharedPreferences.getString(Constants.PREF_FILTER_GENRE_ID, null)
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_FILTER_GENRE_ID, value)
            }
        }

    var filterGenreName: String?
        get() = sharedPreferences.getString(Constants.PREF_FILTER_GENRE_NAME, null)
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_FILTER_GENRE_NAME, value)
            }
        }

    var filterYearId: String?
        get() = sharedPreferences.getString(Constants.PREF_FILTER_YEAR_ID, null)
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_FILTER_YEAR_ID, value)
            }
        }

    var filterYearName: String?
        get() = sharedPreferences.getString(Constants.PREF_FILTER_YEAR_NAME, null)
        set(value) {
            sharedPreferences.edit {
                putString(Constants.PREF_FILTER_YEAR_NAME, value)
            }
        }

    // ========== ADVANCED UTILITY METHODS ==========
    /**
     * Generic preference setters with validation
     * 🔧 Thread-safe preference access
     */
    fun setValue(key: String, value: String) {
        validatePreferenceKey(key)
        sharedPreferences.edit {
            putString(key, value)
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        validatePreferenceKey(key)
        return sharedPreferences.getBoolean(key, default)
    }

    fun setBoolean(key: String, value: Boolean) {
        validatePreferenceKey(key)
        sharedPreferences.edit {
            putBoolean(key, value)
        }
    }

    fun getString(key: String, default: String?): String? {
        validatePreferenceKey(key)
        return sharedPreferences.getString(key, default)
    }

    fun setString(key: String, value: String?) {
        validatePreferenceKey(key)
        sharedPreferences.edit {
            putString(key, value)
        }
    }

    /**
     * Validates preference key to prevent errors
     */
    private fun validatePreferenceKey(key: String) {
        if (key.isBlank()) {
            throw IllegalArgumentException("Preference key cannot be blank")
        }
        
        // Advanced: Log preference access for debugging
        if (Constants.ENABLE_NETWORK_LOGGING) {
            println("🔑 Accessing preference: $key")
        }
    }

    // ========== ADVANCED FEATURE METHODS ==========
    /**
     * Check if advanced features are enabled
     */
    fun isAdvancedModeEnabled(): Boolean {
        return getBoolean(Constants.ADVANCED_FEATURES_ENABLED, false) &&
               BuildConfig.IS_PROPERLY_CONFIGURED
    }

    /**
     * Get build information for debugging
     */
    fun getBuildInfo(): Map<String, String> {
        return mapOf(
            "builder" to BuildConfig.BUILDER_NAME,
            "environment" to BuildConfig.BUILD_ENVIRONMENT,
            "timestamp" to BuildConfig.BUILD_TIMESTAMP.toString(),
            "advanced_mode" to isAdvancedModeEnabled().toString()
        )
    }

    /**
     * Reset preferences to default values
     * ⚠️  Use with caution - this will reset all user preferences
     */
    fun resetToDefaults() {
        sharedPreferences.edit {
            clear()
        }
        initializeAdvancedFeatures()
        println("🔄 Preferences reset to defaults")
    }
}
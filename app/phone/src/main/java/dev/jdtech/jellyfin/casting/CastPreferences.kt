package dev.jdtech.jellyfin.casting

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager để lưu trữ cast preferences và settings
 */
@Singleton
class CastPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREF_NAME = "cast_preferences"
        private const val KEY_LAST_DEVICE_NAME = "last_device_name"
        private const val KEY_LAST_DEVICE_ID = "last_device_id"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_CAST_QUALITY = "cast_quality"
        private const val KEY_CAST_AUDIO = "cast_audio"
        private const val KEY_SHOW_CAST_NOTIFICATION = "show_cast_notification"
        private const val KEY_CAST_SESSIONS_COUNT = "cast_sessions_count"
        private const val KEY_FIRST_TIME_CAST = "first_time_cast"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Lưu thông tin device đã connect gần nhất
     */
    fun saveLastConnectedDevice(deviceName: String, deviceId: String) {
        prefs.edit()
            .putString(KEY_LAST_DEVICE_NAME, deviceName)
            .putString(KEY_LAST_DEVICE_ID, deviceId)
            .apply()
        
        Timber.d("Saved last connected device: $deviceName")
    }
    
    /**
     * Lấy thông tin device đã connect gần nhất
     */
    fun getLastConnectedDevice(): Pair<String?, String?> {
        val deviceName = prefs.getString(KEY_LAST_DEVICE_NAME, null)
        val deviceId = prefs.getString(KEY_LAST_DEVICE_ID, null)
        return Pair(deviceName, deviceId)
    }
    
    /**
     * Có nên auto connect tới device gần nhất không
     */
    var shouldAutoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()
        }
    
    /**
     * Cast quality setting (720p, 1080p, auto)
     */
    var castQuality: CastQuality
        get() {
            val qualityName = prefs.getString(KEY_CAST_QUALITY, CastQuality.AUTO.name)
            return try {
                CastQuality.valueOf(qualityName ?: CastQuality.AUTO.name)
            } catch (e: Exception) {
                CastQuality.AUTO
            }
        }
        set(value) {
            prefs.edit().putString(KEY_CAST_QUALITY, value.name).apply()
        }
    
    /**
     * Có cast audio cùng với video không
     */
    var shouldCastAudio: Boolean
        get() = prefs.getBoolean(KEY_CAST_AUDIO, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CAST_AUDIO, value).apply()
        }
    
    /**
     * Có hiển thị notification khi cast không
     */
    var shouldShowCastNotification: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CAST_NOTIFICATION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_CAST_NOTIFICATION, value).apply()
        }
    
    /**
     * Số lần đã cast (để analytics)
     */
    var castSessionsCount: Int
        get() = prefs.getInt(KEY_CAST_SESSIONS_COUNT, 0)
        private set(value) {
            prefs.edit().putInt(KEY_CAST_SESSIONS_COUNT, value).apply()
        }
    
    /**
     * Có phải lần đầu cast không
     */
    var isFirstTimeCast: Boolean
        get() = prefs.getBoolean(KEY_FIRST_TIME_CAST, true)
        private set(value) {
            prefs.edit().putBoolean(KEY_FIRST_TIME_CAST, value).apply()
        }
    
    /**
     * Increment cast session count
     */
    fun incrementCastSession() {
        castSessionsCount = castSessionsCount + 1
        if (isFirstTimeCast) {
            isFirstTimeCast = false
        }
        Timber.d("Cast sessions count: $castSessionsCount")
    }
    
    /**
     * Clear tất cả cast preferences
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Timber.d("Cast preferences cleared")
    }
    
    /**
     * Clear last connected device
     */
    fun clearLastConnectedDevice() {
        prefs.edit()
            .remove(KEY_LAST_DEVICE_NAME)
            .remove(KEY_LAST_DEVICE_ID)
            .apply()
        Timber.d("Last connected device cleared")
    }
    
    /**
     * Get all preferences for debugging
     */
    fun getAllPreferences(): Map<String, Any?> {
        return prefs.all
    }
}

/**
 * Enum cho cast quality options
 */
enum class CastQuality(val displayName: String, val resolution: Pair<Int, Int>?) {
    AUTO("Auto", null),
    HD_720P("720p", Pair(1280, 720)),
    HD_1080P("1080p", Pair(1920, 1080)),
    SD_480P("480p", Pair(854, 480))
}
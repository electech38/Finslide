package dev.jdtech.jellyfin.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import timber.log.Timber

/**
 * Utility functions cho cast functionality
 */
object CastUtils {
    
    /**
     * Kiểm tra xem device có connected tới WiFi không
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Lấy WiFi SSID hiện tại
     */
    fun getCurrentWifiSSID(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo.ssid?.replace("\"", "") // Remove quotes từ SSID
        } catch (e: Exception) {
            Timber.e(e, "Failed to get WiFi SSID")
            null
        }
    }
    
    /**
     * Kiểm tra xem có permission để access WiFi state không
     */
    fun hasWifiPermission(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
            true
        } catch (e: SecurityException) {
            Timber.e(e, "No WiFi permission")
            false
        }
    }
    
    /**
     * Format device name để hiển thị trong UI
     */
    fun formatDeviceName(deviceName: String): String {
        return when {
            deviceName.isBlank() -> "Unknown Device"
            deviceName.length > 30 -> "${deviceName.take(27)}..."
            else -> deviceName
        }
    }
    
    /**
     * Tạo unique ID cho cast session
     */
    fun generateCastSessionId(): String {
        return "cast_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    /**
     * Kiểm tra xem device có hỗ trợ screen mirroring không
     */
    fun isScreenMirroringSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }
    
    /**
     * Tạo notification ID unique cho cast service
     */
    fun getCastNotificationId(): Int {
        return 1001 // Fixed ID cho cast notifications
    }
    
    /**
     * Format thời gian cast duration
     */
    fun formatCastDuration(startTimeMillis: Long): String {
        val duration = System.currentTimeMillis() - startTimeMillis
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60)) % 24
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            else -> String.format("00:%02d", seconds)
        }
    }
    
    /**
     * Validation cho cast device
     */
    fun isValidCastDevice(deviceName: String?, deviceId: String?): Boolean {
        return !deviceName.isNullOrBlank() && !deviceId.isNullOrBlank()
    }
    
    /**
     * Get optimal screen resolution cho casting
     */
    fun getOptimalCastResolution(context: Context): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Reduce resolution để improve performance
        return when {
            screenWidth > 1920 -> Pair(1920, 1080) // 1080p max
            screenWidth > 1280 -> Pair(screenWidth / 2, screenHeight / 2) // Half resolution
            else -> Pair(screenWidth, screenHeight) // Original resolution
        }
    }
}

/**
 * Extension functions
 */

/**
 * Extension để check nếu string là valid device name
 */
fun String?.isValidDeviceName(): Boolean {
    return !this.isNullOrBlank() && this.length >= 2
}

/**
 * Extension để truncate device description
 */
fun String.truncateDescription(maxLength: Int = 50): String {
    return if (this.length > maxLength) {
        "${this.take(maxLength - 3)}..."
    } else {
        this
    }
}
package dev.jdtech.jellyfin.casting

import android.content.Context
import android.content.Intent
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.RouteInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class để quản lý cast devices - SIMPLE VERSION
 */
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val mediaRouter = MediaRouter.getInstance(context)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    // State cho available devices
    private val _availableDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val availableDevices: StateFlow<List<CastDevice>> = _availableDevices.asStateFlow()

    // State cho connected device
    private val _connectedDevice = MutableStateFlow<CastDevice?>(null)
    val connectedDevice: StateFlow<CastDevice?> = _connectedDevice.asStateFlow()

    // State cho casting status
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private var isScanning = false

    /**
     * MediaRouter callback để detect devices
     */
    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: RouteInfo) {
            Timber.d("Cast device added: ${route.name}")
            updateAvailableDevices()
        }

        override fun onRouteRemoved(router: MediaRouter, route: RouteInfo) {
            Timber.d("Cast device removed: ${route.name}")
            updateAvailableDevices()
        }

        override fun onRouteChanged(router: MediaRouter, route: RouteInfo) {
            Timber.d("Cast device changed: ${route.name}")
            updateAvailableDevices()
        }

        override fun onRouteSelected(router: MediaRouter, route: RouteInfo) {
            Timber.d("Cast device selected: ${route.name}")
            val device = CastDevice(
                id = route.id,
                name = route.name,
                description = route.description ?: "",
                isConnected = true,
                routeInfo = route
            )
            _connectedDevice.value = device
            _isCasting.value = true

            Timber.d("Cast started - Let Android handle everything")
        }

        override fun onRouteUnselected(router: MediaRouter, route: RouteInfo, reason: Int) {
            Timber.d("Cast device unselected: ${route.name}")
            _connectedDevice.value = null
            _isCasting.value = false
        }
    }

    /**
     * Bắt đầu scan devices
     */
    fun startDeviceDiscovery() {
        if (!isScanning) {
            Timber.d("Starting cast device discovery...")
            mediaRouter.addCallback(selector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            isScanning = true
            updateAvailableDevices()
        }
    }

    /**
     * Dừng scan devices
     */
    fun stopDeviceDiscovery() {
        if (isScanning) {
            Timber.d("Stopping cast device discovery...")
            mediaRouter.removeCallback(mediaRouterCallback)
            isScanning = false
        }
    }

    /**
     * Connect tới device và trigger built-in screen mirroring
     */
    fun connectToDevice(device: CastDevice) {
        Timber.d("Connecting to device: ${device.name}")
        device.routeInfo?.let { route ->
            // Select route trong MediaRouter
            mediaRouter.selectRoute(route)

            // Trigger Android's built-in screen mirroring
            triggerBuiltInScreenMirroring()
        }
    }

    /**
     * Trigger Android's built-in screen mirroring
     */
    private fun triggerBuiltInScreenMirroring() {
        try {
            // Mở Android's screen mirroring settings
            val intent = Intent("android.settings.CAST_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)

            Timber.d("Opened Android Cast Settings - Android will handle wake locks")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open Cast Settings")
        }
    }

    /**
     * Disconnect khỏi current device
     */
    fun disconnect() {
        Timber.d("Disconnecting from cast device...")
        try {
            // Sử dụng default route thay vì unselect với reason
            mediaRouter.selectRoute(mediaRouter.defaultRoute)
        } catch (e: Exception) {
            Timber.e(e, "Failed to disconnect from cast device")
        }

        _connectedDevice.value = null
        _isCasting.value = false
    }

    /**
     * Bắt đầu screen mirroring với built-in Android casting
     */
    fun startScreenMirroring(device: CastDevice) {
        Timber.d("Starting screen mirroring to: ${device.name}")

        // Trigger built-in screen mirroring
        triggerBuiltInScreenMirroring()
        _isCasting.value = true
    }

    /**
     * Dừng screen mirroring
     */
    fun stopScreenMirroring() {
        Timber.d("Stopping screen mirroring...")
        disconnect()
    }

    /**
     * Update danh sách available devices
     */
    private fun updateAvailableDevices() {
        val routes = mediaRouter.routes
        val devices = routes.mapNotNull { route ->
            if (route.isDefault) return@mapNotNull null

            CastDevice(
                id = route.id,
                name = route.name,
                description = route.description ?: "",
                isConnected = route.isSelected,
                routeInfo = route
            )
        }

        Timber.d("Available cast devices: ${devices.size}")
        _availableDevices.value = devices
    }

    /**
     * Get current cast state
     */
    fun getCastState(): CastState {
        return when {
            _isCasting.value && _connectedDevice.value != null -> CastState.CONNECTED
            isScanning -> CastState.SCANNING
            else -> CastState.DISCONNECTED
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopDeviceDiscovery()
        stopScreenMirroring()
    }
}

/**
 * Data class cho cast device
 */
data class CastDevice(
    val id: String,
    val name: String,
    val description: String,
    val isConnected: Boolean = false,
    val routeInfo: RouteInfo? = null
)

/**
 * Enum cho cast states
 */
enum class CastState {
    DISCONNECTED,
    SCANNING,
    CONNECTED
}
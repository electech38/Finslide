package dev.jdtech.jellyfin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.casting.CastDevice
import dev.jdtech.jellyfin.casting.CastManager
import dev.jdtech.jellyfin.casting.CastState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import android.content.Context

/**
 * ViewModel để quản lý cast functionality
 */
@HiltViewModel
class CastViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    // Manual CastManager creation to avoid Hilt conflicts
    private val castManager by lazy { CastManager(context) }
    
    // Expose cast manager states
    val availableDevices: StateFlow<List<CastDevice>> = castManager.availableDevices
    val connectedDevice: StateFlow<CastDevice?> = castManager.connectedDevice
    val isCasting: StateFlow<Boolean> = castManager.isCasting
    
    // UI states
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Bắt đầu scan devices
     */
    fun startDeviceDiscovery() {
        viewModelScope.launch {
            try {
                _isScanning.value = true
                _error.value = null
                castManager.startDeviceDiscovery()
                Timber.d("Device discovery started")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start device discovery")
                _error.value = "Failed to scan for devices: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    /**
     * Dừng scan devices
     */
    fun stopDeviceDiscovery() {
        viewModelScope.launch {
            try {
                castManager.stopDeviceDiscovery()
                _isScanning.value = false
                Timber.d("Device discovery stopped")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop device discovery")
            }
        }
    }
    
    /**
     * Connect tới device
     */
    fun connectToDevice(device: CastDevice) {
        viewModelScope.launch {
            try {
                _error.value = null
                castManager.connectToDevice(device)
                Timber.d("Connecting to device: ${device.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to device: ${device.name}")
                _error.value = "Failed to connect to ${device.name}: ${e.message}"
            }
        }
    }
    
    /**
     * Bắt đầu screen mirroring
     */
    fun startScreenMirroring(device: CastDevice) {
        viewModelScope.launch {
            try {
                _error.value = null
                castManager.startScreenMirroring(device)
                Timber.d("Screen mirroring started to: ${device.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start screen mirroring to: ${device.name}")
                _error.value = "Failed to start screen mirroring: ${e.message}"
            }
        }
    }
    
    /**
     * Dừng screen mirroring
     */
    fun stopScreenMirroring() {
        viewModelScope.launch {
            try {
                _error.value = null
                castManager.stopScreenMirroring()
                Timber.d("Screen mirroring stopped")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop screen mirroring")
                _error.value = "Failed to stop screen mirroring: ${e.message}"
            }
        }
    }
    
    /**
     * Disconnect khỏi current device
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                _error.value = null
                castManager.disconnect()
                Timber.d("Disconnected from cast device")
            } catch (e: Exception) {
                Timber.e(e, "Failed to disconnect")
                _error.value = "Failed to disconnect: ${e.message}"
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Get current cast state
     */
    fun getCastState(): CastState {
        return castManager.getCastState()
    }
    
    /**
     * Refresh device list
     */
    fun refreshDevices() {
        viewModelScope.launch {
            stopDeviceDiscovery()
            startDeviceDiscovery()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup khi ViewModel bị destroyed
        castManager.cleanup()
        Timber.d("CastViewModel cleared")
    }
}
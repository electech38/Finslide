package dev.jdtech.jellyfin.utils

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes
import timber.log.Timber

object DeviceCapabilityChecker {
    
    data class DeviceCapabilities(
        val supports4K: Boolean,
        val supportsHEVC: Boolean,
        val supportsAV1: Boolean,
        val supportsVP9: Boolean,
        val supportedCodecs: List<String>,
        val recommendedPlayer: PlayerType,
        val maxSupportedResolution: String
    )
    
    enum class PlayerType {
        EXOPLAYER, // For standard playback with hardware acceleration
        MPV,       // For advanced codecs/4K when hardware fails
        AUTO       // Let system decide based on content
    }
    
    fun checkDeviceCapabilities(context: Context? = null): DeviceCapabilities {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val supportedCodecs = mutableListOf<String>()
        
        var supports4K = false
        var supportsHEVC = false
        var supportsAV1 = false
        var supportsVP9 = false
        var maxWidth = 0
        var maxHeight = 0
        
        // 🚀 Check hardware decoder capabilities
        for (codecInfo in codecList.codecInfos) {
            if (codecInfo.isEncoder) continue
            
            for (type in codecInfo.supportedTypes) {
                supportedCodecs.add("${codecInfo.name}: $type")
                
                try {
                    val capabilities = codecInfo.getCapabilitiesForType(type)
                    val videoCapabilities = capabilities.videoCapabilities
                    
                    if (videoCapabilities != null) {
                        // Check maximum resolution
                        val widthRange = videoCapabilities.supportedWidths
                        val heightRange = videoCapabilities.supportedHeights
                        
                        maxWidth = maxOf(maxWidth, widthRange.upper)
                        maxHeight = maxOf(maxHeight, heightRange.upper)
                        
                        // Check 4K support (3840x2160 or higher)
                        if (widthRange.upper >= 3840 && heightRange.upper >= 2160) {
                            supports4K = true
                        }
                    }
                    
                    // 🚀 Check codec support based on MIME types
                    when (type.lowercase()) {
                        MimeTypes.VIDEO_H265.lowercase(), 
                        "video/hevc",
                        "video/x-vnd.on2.vp9" -> {
                            if (type.contains("hevc", ignoreCase = true) || 
                                type.contains("h265", ignoreCase = true)) {
                                supportsHEVC = true
                                Timber.d("HEVC support: ${codecInfo.name}")
                            }
                        }
                        MimeTypes.VIDEO_VP9.lowercase() -> {
                            supportsVP9 = true
                            Timber.d("VP9 support: ${codecInfo.name}")
                        }
                        "video/av01" -> {
                            supportsAV1 = true
                            Timber.d("AV1 support: ${codecInfo.name}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w("Error checking codec capabilities for $type: ${e.message}")
                }
            }
        }
        
        // 🚀 Additional checks for specific Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ has better AV1 support
            supportsAV1 = supportsAV1 || checkAV1Support()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5+ has better HEVC support
            supportsHEVC = supportsHEVC || checkHEVCSupport()
        }
        
        // 🚀 Determine recommended player based on capabilities
        val recommendedPlayer = when {
            // Use MPV for:
            // - Devices without good hardware acceleration
            // - Advanced codecs that might have issues in ExoPlayer
            // - Older Android versions
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> PlayerType.MPV
            !supports4K && (supportsHEVC || supportsAV1) -> PlayerType.MPV
            
            // Use ExoPlayer for:
            // - Modern devices with hardware acceleration
            // - Standard H.264/VP9 content
            else -> PlayerType.EXOPLAYER
        }
        
        val maxResolution = "${maxWidth}x${maxHeight}"
        
        Timber.i("""
            🚀 Device Capabilities Analysis:
            =====================================
            📱 Device: ${Build.MANUFACTURER} ${Build.MODEL}
            🤖 Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
            📺 Max Resolution: $maxResolution
            🎬 4K Support: $supports4K
            
            📊 Codec Support:
            - H.264/AVC: ✅ (Universal)
            - HEVC/H.265: ${if (supportsHEVC) "✅" else "❌"}
            - VP9: ${if (supportsVP9) "✅" else "❌"}  
            - AV1: ${if (supportsAV1) "✅" else "❌"}
            
            🎯 Recommended Player: $recommendedPlayer
            =====================================
        """.trimIndent())
        
        return DeviceCapabilities(
            supports4K = supports4K,
            supportsHEVC = supportsHEVC,
            supportsAV1 = supportsAV1,
            supportsVP9 = supportsVP9,
            supportedCodecs = supportedCodecs,
            recommendedPlayer = recommendedPlayer,
            maxSupportedResolution = maxResolution
        )
    }
    
    private fun checkHEVCSupport(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { codecInfo ->
                !codecInfo.isEncoder && codecInfo.supportedTypes.any { type ->
                    type.equals(MimeTypes.VIDEO_H265, ignoreCase = true) ||
                    type.contains("hevc", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkAV1Support(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { codecInfo ->
                !codecInfo.isEncoder && codecInfo.supportedTypes.any { type ->
                    type.contains("av01", ignoreCase = true) ||
                    type.contains("av1", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // 🚀 Check if direct play is recommended for specific content
    fun shouldUseDirectPlay(
        mimeType: String, 
        width: Int, 
        height: Int,
        bitrate: Int = 0
    ): Pair<Boolean, String> {
        val capabilities = checkDeviceCapabilities()
        
        val recommendation = when {
            // HEVC/H.265 content
            mimeType.contains("hevc", ignoreCase = true) || 
            mimeType.contains("h265", ignoreCase = true) -> {
                if (capabilities.supportsHEVC && (width <= 3840 || capabilities.supports4K)) {
                    true to "✅ HEVC hardware decoder available"
                } else {
                    false to "❌ HEVC not supported or resolution too high"
                }
            }
            
            // AV1 content
            mimeType.contains("av01", ignoreCase = true) -> {
                if (capabilities.supportsAV1) {
                    true to "✅ AV1 hardware decoder available"
                } else {
                    false to "❌ AV1 decoder not available, will use software (MPV)"
                }
            }
            
            // VP9 content
            mimeType.contains("vp9", ignoreCase = true) -> {
                if (capabilities.supportsVP9) {
                    true to "✅ VP9 hardware decoder available"
                } else {
                    false to "❌ VP9 decoder limited, may need software decode"
                }
            }
            
            // H.264/AVC content (universal support)
            mimeType.contains("avc", ignoreCase = true) || 
            mimeType.contains("h264", ignoreCase = true) -> {
                if (width >= 3840 && !capabilities.supports4K) {
                    false to "❌ 4K H.264 not supported on this device"
                } else {
                    true to "✅ H.264 universally supported"
                }
            }
            
            else -> true to "✅ Unknown codec, attempting direct play"
        }
        
        Timber.d("🎬 Direct Play Analysis: $mimeType ${width}x${height} -> ${recommendation.second}")
        return recommendation
    }
    
    // 🚀 Get optimal buffer size based on resolution and codec
    fun getOptimalBufferSize(width: Int, height: Int, mimeType: String = ""): Int {
        val baseSize = when {
            width >= 3840 -> 128 * 1024 * 1024  // 128MB for 4K
            width >= 1920 -> 64 * 1024 * 1024   // 64MB for 1080p
            width >= 1280 -> 32 * 1024 * 1024   // 32MB for 720p
            else -> 16 * 1024 * 1024             // 16MB for SD
        }
        
        // Increase buffer for software-decoded codecs
        val multiplier = when {
            mimeType.contains("hevc", ignoreCase = true) && !checkHEVCSupport() -> 1.5f
            mimeType.contains("av01", ignoreCase = true) && !checkAV1Support() -> 2.0f
            else -> 1.0f
        }
        
        return (baseSize * multiplier).toInt()
    }
    
    // 🚀 Get optimal track selector settings
    fun getOptimalTrackSelectorSettings(): TrackSelectorSettings {
        val capabilities = checkDeviceCapabilities()
        
        return TrackSelectorSettings(
            maxVideoWidth = if (capabilities.supports4K) Int.MAX_VALUE else 1920,
            maxVideoHeight = if (capabilities.supports4K) Int.MAX_VALUE else 1080,
            maxVideoBitrate = if (capabilities.supports4K) Int.MAX_VALUE else 10_000_000, // 10Mbps limit for non-4K
            preferredVideoCodecs = buildList {
                if (capabilities.supportsHEVC) add(MimeTypes.VIDEO_H265)
                if (capabilities.supportsVP9) add(MimeTypes.VIDEO_VP9)
                add(MimeTypes.VIDEO_H264) // Always include H.264 as fallback
            }
        )
    }
    
    data class TrackSelectorSettings(
        val maxVideoWidth: Int,
        val maxVideoHeight: Int,
        val maxVideoBitrate: Int,
        val preferredVideoCodecs: List<String>
    )
}
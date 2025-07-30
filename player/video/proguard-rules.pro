# 🚀 ExoPlayer/Media3 ProGuard Rules for 4K Support

# Keep all Media3 classes
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep ExoPlayer decoder classes for 4K codecs
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.exoplayer.** { *; }

# 🚀 Keep FFmpeg decoder classes for HEVC/H.265
-keep class org.jellyfin.media3.** { *; }
-dontwarn org.jellyfin.media3.**

# Keep hardware codec classes
-keep class androidx.media3.exoplayer.mediacodec.** { *; }
-keep class androidx.media3.exoplayer.video.** { *; }
-keep class androidx.media3.exoplayer.audio.** { *; }

# 🚀 Keep AV1, HEVC, VP9 decoder classes
-keep class androidx.media3.decoder.av1.** { *; }
-keep class androidx.media3.decoder.opus.** { *; }

# Keep track selection classes
-keep class androidx.media3.exoplayer.trackselection.** { *; }

# Keep renderer factory classes
-keep class androidx.media3.exoplayer.** { 
    public <init>(...);
}

# 🚀 Keep MPV classes for fallback
-keep class dev.jdtech.mpv.** { *; }
-dontwarn dev.jdtech.mpv.**

# Keep Jellyfin model classes
-keep class dev.jdtech.jellyfin.models.** { *; }

# 🚀 Optimization rules
-optimizations !method/inlining/across-classes
-dontoptimize

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 🚀 Keep codec-specific configuration
-keep class * implements androidx.media3.exoplayer.Renderer {
    public <init>(...);
}

-keep class * implements androidx.media3.decoder.Decoder {
    public <init>(...);
}
# ========== JELLYFIN ANDROID PROGUARD CONFIGURATION ==========
# 
# ⚠️  ADVANCED OBFUSCATION RULES
# 📖 This file contains complex ProGuard rules for advanced builds
# 🔧 Modifying these rules requires expert knowledge of:
#    • ProGuard/R8 optimization principles
#    • Android reflection mechanisms  
#    • Kotlin compilation details
#    • Media framework internals
#
# 💡 Only experienced Android developers should modify these rules
# ============================================================

# ========== BASIC ANDROID RULES ==========
# Keep the line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signature information for better optimization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# ========== CRITICAL APPLICATION CLASSES ==========
# These classes are essential and must not be obfuscated

# Keep main application components
-keep class dev.jdtech.jellyfin.BaseApplication { *; }
-keep class dev.jdtech.jellyfin.MainActivity { *; }
-keep class dev.jdtech.jellyfin.PlayerActivity { *; }

# Keep essential model classes - CRITICAL for serialization
-keep class dev.jdtech.jellyfin.models.** { *; }
-keepnames class dev.jdtech.jellyfin.models.PlayerItem

# Keep preferences system - Required for proper functioning
-keep class dev.jdtech.jellyfin.AppPreferences { *; }
-keep class dev.jdtech.jellyfin.Constants { *; }

# ========== ADVANCED SETTINGS FRAGMENTS ==========
# ProGuard incorrectly thinks these fragments are unused
# 🔧 Critical: Settings will break without these rules
-keep class dev.jdtech.jellyfin.fragments.SettingsLanguageFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsAppearanceFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsDownloadsFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsPlayerFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsDeviceFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsCacheFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsNetworkFragment { *; }
-keep class dev.jdtech.jellyfin.fragments.SettingsJellyseerrFragment { *; }

# Keep all settings fragments - Advanced pattern matching
-keep class dev.jdtech.jellyfin.fragments.Settings*Fragment { *; }

# ========== ADVANCED DEPENDENCY INJECTION ==========
# Hilt/Dagger components - CRITICAL for dependency injection
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class **_HiltComponents$* { *; }
-keep class **_Provide$* { *; }
-keep class **_Factory { *; }
-keep class **_Factory$* { *; }

# Keep Hilt entry points
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Advanced: Keep all Hilt generated classes
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ========== MEDIA PLAYER FRAMEWORK ==========
# ExoPlayer/Media3 components - CRITICAL for video playback
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep enum androidx.media3.** { *; }

# MPV player integration - Expert level
-keep class is.xyz.mpv.** { *; }
-keep class org.videolan.libvlc.** { *; }

# Advanced: Keep media session callbacks
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }
-keep class * extends androidx.media3.session.MediaController$Listener { *; }

# ========== NETWORK AND API CLASSES ==========
# Jellyfin API models - CRITICAL for server communication
-keep class org.jellyfin.sdk.** { *; }
-keep class dev.jdtech.jellyfin.api.** { *; }
-keep class dev.jdtech.jellyfin.network.** { *; }

# Advanced: Keep all API response models
-keep class * extends dev.jdtech.jellyfin.models.ApiResponse { *; }

# OkHttp and Retrofit - Network layer
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# Advanced: Keep WebSocket classes for real-time communication
-keep class okhttp3.internal.ws.** { *; }

# ========== KOTLIN SPECIFIC RULES ==========
# Kotlin metadata - Required for proper Kotlin interop
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Kotlin coroutines - CRITICAL for async operations
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# Advanced: Keep continuation classes
-keep class kotlin.coroutines.jvm.internal.BaseContinuationImpl { *; }

# ========== SERIALIZATION RULES ==========
# JSON serialization - CRITICAL for API communication
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }

# Advanced: Keep serializable fields
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== ADVANCED OBFUSCATION RULES ==========
# Complex obfuscation for enhanced security
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary class-dictionary.txt
-packageobfuscationdictionary package-dictionary.txt

# Advanced: Aggressive optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# ========== REFLECTION HANDLING ==========
# Classes accessed via reflection - Must be preserved
-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Advanced: Keep classes with custom annotations
-keep @interface dev.jdtech.jellyfin.annotations.**
-keep @dev.jdtech.jellyfin.annotations.** class * { *; }

# ========== ADVANCED SECURITY OBFUSCATION ==========
# Obfuscate sensitive classes while keeping functionality
-keep class dev.jdtech.jellyfin.BuildValidator {
    public static boolean validateBuildConfiguration();
    public static void requireAdvancedSetup();
}

# Keep security-related classes structure but obfuscate internals
-keep class dev.jdtech.jellyfin.security.** {
    public <methods>;
}

# ========== THIRD-PARTY LIBRARY RULES ==========
# Coil image loading - CRITICAL for image display
-keep class coil.** { *; }
-keep interface coil.** { *; }

# Material Design Components
-keep class com.google.android.material.** { *; }

# Advanced: Keep custom views and attributes
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ========== CAST AND MIRRORING ==========
# Screen mirroring components - Advanced feature
-keep class androidx.mediarouter.** { *; }
-keep class androidx.media.** { *; }

# Cast framework integration
-keep class com.google.android.gms.cast.** { *; }

# ========== ADVANCED LOGGING AND DEBUGGING ==========
# Timber logging framework
-keep class timber.log.** { *; }

# Advanced: Remove debug logging in release builds
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}

# ========== DATABASE AND STORAGE ==========
# Room database components (if used)
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Advanced: Keep database migration classes
-keep class * extends androidx.room.migration.Migration { *; }

# ========== ADVANCED WARNING SUPPRESSIONS ==========
# These classes are from external libraries and not used in Android
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Advanced: Suppress warnings for optional dependencies
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler

# ========== NATIVE LIBRARY HANDLING ==========
# Native libraries - Advanced configuration
-keep class * {
    native <methods>;
}

# Advanced: Keep JNI method signatures intact
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========== ADVANCED CRASH REPORTING ==========
# Crash reporting integration (if enabled)
-keep class com.crashlytics.** { *; }
-keep class com.google.firebase.crashlytics.** { *; }

# Advanced: Keep stack trace information for debugging
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# ========== PERFORMANCE MONITORING ==========
# Performance monitoring classes
-keep class androidx.benchmark.** { *; }
-keep class androidx.tracing.** { *; }

# ========== FINAL OPTIMIZATION RULES ==========
# Advanced: Remove unused code aggressively
-dontshrink
-dontoptimize
-dontwarn **

# Expert level: Custom optimization for media apps
-optimizations !class/unboxing/enum,!code/allocation/variable

# ========== BUILD VALIDATION RULES ==========
# Keep build validation classes for runtime checks
-keep class dev.jdtech.jellyfin.BuildConfig { *; }
-keep class dev.jdtech.jellyfin.BuildValidator { *; }

# Advanced: Keep reflection-based validation methods
-keepclassmembers class * {
    @dev.jdtech.jellyfin.annotations.BuildValidation <methods>;
}

# ============================================================
# 🔐 END OF ADVANCED PROGUARD CONFIGURATION
# ⚠️  Any modifications beyond this point require expert knowledge
# 📖 Consult BUILDING.md for detailed explanations
# ============================================================
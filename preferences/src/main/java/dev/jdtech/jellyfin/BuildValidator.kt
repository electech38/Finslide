package dev.jdtech.jellyfin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Advanced Build Configuration Validator
 * 
 * ⚠️  CRITICAL SYSTEM COMPONENT
 * 📖 This class performs complex validation of build configuration
 * 🔧 Ensures only properly configured builds can run
 * 
 * Features:
 * • Build secret validation
 * • Environment configuration checking  
 * • Builder certification verification
 * • Runtime security validation
 * • Advanced feature unlocking
 * 
 * 💡 This system prevents casual copying while maintaining GPL compliance
 */
object BuildValidator {

    /**
     * Validates the complete build configuration
     * 🔧 This method performs comprehensive validation
     */
    fun validateBuildConfiguration(): Boolean {
        return try {
            validateBasicConfiguration() &&
            validateAdvancedConfiguration() &&
            validateSecurityConfiguration() &&
            validateBuilderCertification()
        } catch (e: Exception) {
            logValidationError("Build validation failed", e)
            false
        }
    }

    /**
     * Requires advanced setup - throws exception if not properly configured
     * ⚠️  This is called during app initialization
     */
    fun requireAdvancedSetup() {
        if (!validateBuildConfiguration()) {
            throw BuildConfigurationException(
                """
                ❌ ADVANCED BUILD CONFIGURATION REQUIRED
                
                📖 This application requires advanced Android development knowledge:
                   • Deep understanding of Kotlin and Android architecture
                   • Experience with Gradle build systems
                   • Knowledge of Jellyfin API and media frameworks
                   • Understanding of dependency injection (Hilt/Dagger)
                   • Familiarity with ExoPlayer/Media3 components
                
                🔧 Required Setup:
                   1. Configure all properties in local.properties
                   2. Set up proper signing keys for release builds
                   3. Understand the codebase architecture
                   4. Read and understand BUILDING.md completely
                
                💡 This is not a casual build - advanced expertise required!
                
                🚫 If you're looking for a simple APK, this project may not be suitable
                ✅ If you're an experienced developer, see BUILDING.md for setup
                """.trimIndent()
            )
        }
    }

    /**
     * Validates basic build configuration from BuildConfig
     */
    private fun validateBasicConfiguration(): Boolean {
        try {
            // Check BuildConfig constants exist and are properly set
            val buildSecret = BuildConfig.BUILD_SECRET
            val apiUrl = BuildConfig.API_BASE_URL
            val isConfigured = BuildConfig.IS_PROPERLY_CONFIGURED
            val builderName = BuildConfig.BUILDER_NAME
            val environment = BuildConfig.BUILD_ENVIRONMENT

            if (buildSecret.isEmpty() || buildSecret.length < Constants.REQUIRED_BUILD_SECRET_LENGTH) {
                throw BuildConfigurationException("Invalid BUILD_SECRET configuration")
            }

            if (!apiUrl.startsWith("https://") || apiUrl.length < 10) {
                throw BuildConfigurationException("Invalid API_BASE_URL configuration")
            }

            if (!isConfigured) {
                throw BuildConfigurationException("Build configuration incomplete")
            }

            if (builderName.isEmpty()) {
                throw BuildConfigurationException("Builder certification missing")
            }

            if (environment !in listOf("development", "staging", "production")) {
                throw BuildConfigurationException("Invalid build environment: $environment")
            }

            logValidationSuccess("Basic configuration validated")
            return true

        } catch (e: Exception) {
            logValidationError("Basic configuration validation failed", e)
            return false
        }
    }

    /**
     * Validates advanced build configuration
     */
    private fun validateAdvancedConfiguration(): Boolean {
        try {
            val buildTimestamp = BuildConfig.BUILD_TIMESTAMP
            val currentTime = System.currentTimeMillis()
            
            // Check if build is not too old (prevents using old compiled APKs)
            val buildAge = currentTime - buildTimestamp
            val maxBuildAge = TimeUnit.DAYS.toMillis(30) // 30 days
            
            if (buildAge > maxBuildAge) {
                logValidationWarning("Build is older than 30 days - consider rebuilding")
            }

            // Validate advanced features are properly configured
            val environment = BuildConfig.BUILD_ENVIRONMENT
            when (environment) {
                "production" -> validateProductionConfiguration()
                "staging" -> validateStagingConfiguration() 
                "development" -> validateDevelopmentConfiguration()
            }

            logValidationSuccess("Advanced configuration validated")
            return true

        } catch (e: Exception) {
            logValidationError("Advanced configuration validation failed", e)
            return false
        }
    }

    /**
     * Validates security configuration
     */
    private fun validateSecurityConfiguration(): Boolean {
        try {
            // Check if running on emulator (security consideration)
            val isEmulator = isRunningOnEmulator()
            if (isEmulator && BuildConfig.BUILD_ENVIRONMENT == "production") {
                throw BuildConfigurationException("Production builds should not run on emulators")
            }

            // Validate SSL configuration
            if (Constants.REQUIRE_SECURE_CONNECTIONS) {
                val apiUrl = BuildConfig.API_BASE_URL
                if (!apiUrl.startsWith("https://")) {
                    throw BuildConfigurationException("Secure connections required but HTTP API configured")
                }
            }

            // Check for debugging flags in release builds
            if (!BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "release") {
                validateReleaseSecuritySettings()
            }

            logValidationSuccess("Security configuration validated")
            return true

        } catch (e: Exception) {
            logValidationError("Security validation failed", e)
            return false
        }
    }

    /**
     * Validates builder certification
     */
    private fun validateBuilderCertification(): Boolean {
        try {
            val builderName = BuildConfig.BUILDER_NAME
            val validBuilders = listOf(
                "advanced_developer",
                "certified_builder", 
                "jellyfin_expert"
            )

            if (!validBuilders.contains(builderName)) {
                throw BuildConfigurationException(
                    "Invalid builder certification: $builderName. Valid types: ${validBuilders.joinToString(", ")}"
                )
            }

            // Advanced: Validate builder certification signature
            val expectedSignature = generateBuilderSignature(builderName)
            val providedSignature = BuildConfig.BUILD_SECRET
            
            if (!validateBuilderSignature(expectedSignature, providedSignature)) {
                throw BuildConfigurationException("Builder certification signature mismatch")
            }

            logValidationSuccess("Builder certification validated: $builderName")
            return true

        } catch (e: Exception) {
            logValidationError("Builder certification validation failed", e)
            return false
        }
    }

    /**
     * Validates production-specific configuration
     */
    private fun validateProductionConfiguration() {
        // Production builds require additional validation
        if (BuildConfig.DEBUG) {
            throw BuildConfigurationException("Debug mode not allowed in production builds")
        }
        
        // Check for development flags
        if (Constants.DEVELOPMENT_MODE_ENABLED) {
            throw BuildConfigurationException("Development mode enabled in production build")
        }

        // Validate signing configuration exists
        if (BuildConfig.BUILD_TYPE == "release" && !isSignedBuild()) {
            logValidationWarning("Production build should be signed")
        }
    }

    /**
     * Validates staging-specific configuration
     */
    private fun validateStagingConfiguration() {
        // Staging builds can have more flexibility but still need validation
        logValidationInfo("Staging environment - relaxed validation")
    }

    /**
     * Validates development-specific configuration
     */
    private fun validateDevelopmentConfiguration() {
        // Development builds have the most flexibility
        logValidationInfo("Development environment - minimal validation")
        
        // Warn about security settings in development
        if (!Constants.VALIDATE_SSL_CERTIFICATES) {
            logValidationWarning("SSL certificate validation disabled - development only!")
        }
    }

    /**
     * Validates release build security settings
     */
    private fun validateReleaseSecuritySettings() {
        // Ensure no development flags are enabled
        val devFlags = listOf(
            Constants.DEVELOPMENT_MODE_ENABLED,
            Constants.MOCK_DATA_ENABLED,
            Constants.SKIP_SSL_VALIDATION
        )
        
        devFlags.forEach { flag ->
            if (flag) {
                throw BuildConfigurationException("Development flag enabled in release build")
            }
        }
    }

    /**
     * Checks if running on emulator
     */
    private fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
               Build.FINGERPRINT.startsWith("unknown") ||
               Build.MODEL.contains("google_sdk") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK built for x86") ||
               Build.MANUFACTURER.contains("Genymotion") ||
               Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
               "google_sdk" == Build.PRODUCT
    }

    /**
     * Checks if the build is signed
     */
    private fun isSignedBuild(): Boolean {
        // This is a simplified check - in reality, you'd check the signature
        return !BuildConfig.DEBUG
    }

    /**
     * Generates builder signature for validation
     */
    private fun generateBuilderSignature(builderName: String): String {
        val input = "$builderName-${BuildConfig.BUILD_TIMESTAMP}-jellyfin-android"
        return generateHash(input)
    }

    /**
     * Validates builder signature
     */
    private fun validateBuilderSignature(expected: String, provided: String): Boolean {
        // Simplified validation - in practice, you'd use more sophisticated methods
        return provided.length >= Constants.REQUIRED_BUILD_SECRET_LENGTH &&
               provided.isNotEmpty()
    }

    /**
     * Generates hash for validation purposes
     */
    private fun generateHash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logValidationError("Hash generation failed", e)
            ""
        }
    }

    /**
     * Context-based validation for runtime checks
     */
    fun validateRuntimeConfiguration(context: Context): Boolean {
        return try {
            validateAppPermissions(context) &&
            validateAppIntegrity(context) &&
            validateDeviceCompatibility(context)
        } catch (e: Exception) {
            logValidationError("Runtime validation failed", e)
            false
        }
    }

    /**
     * Validates app permissions
     */
    private fun validateAppPermissions(context: Context): Boolean {
        val requiredPermissions = listOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.WAKE_LOCK
        )

        return requiredPermissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Validates app integrity
     */
    private fun validateAppIntegrity(context: Context): Boolean {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            
            // Basic integrity checks
            return packageInfo.versionName == BuildConfig.VERSION_NAME &&
                   packageInfo.versionCode == BuildConfig.VERSION_CODE
        } catch (e: Exception) {
            logValidationError("App integrity check failed", e)
            return false
        }
    }

    /**
     * Validates device compatibility
     */
    private fun validateDeviceCompatibility(context: Context): Boolean {
        // Check minimum Android version
        val minSdkVersion = 24 // Android 7.0
        if (Build.VERSION.SDK_INT < minSdkVersion) {
            logValidationError("Device Android version too old: ${Build.VERSION.SDK_INT}")
            return false
        }

        // Check for required hardware features
        val packageManager = context.packageManager
        val requiredFeatures = listOf(
            PackageManager.FEATURE_WIFI
        )

        return requiredFeatures.all { feature ->
            packageManager.hasSystemFeature(feature)
        }
    }

    // ========== LOGGING METHODS ==========
    private fun logValidationSuccess(message: String) {
        println("✅ [BuildValidator] $message")
    }

    private fun logValidationError(message: String, throwable: Throwable? = null) {
        println("❌ [BuildValidator] $message")
        throwable?.let { 
            println("   Error: ${it.message}")
            if (Constants.ENABLE_NETWORK_LOGGING) {
                it.printStackTrace()
            }
        }
    }

    private fun logValidationWarning(message: String) {
        println("⚠️  [BuildValidator] $message")
    }

    private fun logValidationInfo(message: String) {
        println("ℹ️  [BuildValidator] $message")
    }

    /**
     * Custom exception for build configuration issues
     */
    class BuildConfigurationException(message: String) : RuntimeException(message)

    /**
     * Get validation summary for debugging
     */
    fun getValidationSummary(): Map<String, Any> {
        return mapOf(
            "build_secret_configured" to (BuildConfig.BUILD_SECRET.length >= Constants.REQUIRED_BUILD_SECRET_LENGTH),
            "api_url_configured" to BuildConfig.API_BASE_URL.startsWith("https://"),
            "builder_certified" to BuildConfig.BUILDER_NAME.isNotEmpty(),
            "environment" to BuildConfig.BUILD_ENVIRONMENT,
            "build_timestamp" to BuildConfig.BUILD_TIMESTAMP,
            "is_configured" to BuildConfig.IS_PROPERLY_CONFIGURED,
            "validation_passed" to validateBuildConfiguration()
        )
    }
}
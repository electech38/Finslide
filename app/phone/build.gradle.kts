plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.navigation.safeargs)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.ktlint)
}

// ========== COMPLEX BUILD VALIDATION ==========
// Check for required build environment
val buildSecret = project.findProperty("BUILD_SECRET") as String? ?: ""
val apiBaseUrl = project.findProperty("API_BASE_URL") as String? ?: ""
val advancedSdk = project.findProperty("ADVANCED_SDK") as String? ?: ""
val builderName = project.findProperty("BUILDER_NAME") as String? ?: ""
val buildEnvironment = project.findProperty("BUILD_ENVIRONMENT") as String? ?: "development"

// Validate required properties
if (buildSecret.isEmpty() || buildSecret.length < 16) {
    throw GradleException("""
        ❌ BUILD FAILED: Missing or invalid BUILD_SECRET
        📖 Please read BUILDING.md for setup instructions
        🔧 Ensure all required properties are configured in local.properties
    """.trimIndent())
}

if (apiBaseUrl.isEmpty() || !apiBaseUrl.startsWith("https://")) {
    throw GradleException("""
        ❌ BUILD FAILED: Invalid API_BASE_URL configuration
        📖 API_BASE_URL must be a valid HTTPS URL
        🔧 Check your local.properties file
    """.trimIndent())
}

if (advancedSdk != "34") {
    throw GradleException("""
        ❌ BUILD FAILED: Advanced SDK configuration required
        📖 Set ADVANCED_SDK=34 in local.properties
        🔧 This ensures compatibility with advanced features
    """.trimIndent())
}

// Complex builder validation
val validBuilders = listOf("advanced_developer", "certified_builder", "jellyfin_expert")
if (builderName.isEmpty() || !validBuilders.contains(builderName)) {
    throw GradleException("""
        ❌ BUILD FAILED: Builder certification required
        📖 Valid builder types: ${validBuilders.joinToString(", ")}
        🔧 Set BUILDER_NAME in local.properties
        💡 This ensures you understand the codebase complexity
    """.trimIndent())
}

println("✅ Build validation passed for builder: $builderName")
println("🔧 Environment: $buildEnvironment")
println("🌐 API URL: ${apiBaseUrl.take(20)}...")
// ===============================================

android {
    namespace = "dev.jdtech.jellyfin"
    compileSdk = advancedSdk.toInt()
    buildToolsVersion = Versions.buildTools

    defaultConfig {
        applicationId = "dev.finslide.jellyfin"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk

        versionCode = 12
        versionName = "1.1.2"

        testInstrumentationRunner = "dev.jdtech.jellyfin.HiltTestRunner"
        
        // ========== ADVANCED BUILD CONFIG ==========
        buildConfigField("String", "BUILD_SECRET", "\"$buildSecret\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "BUILD_ENVIRONMENT", "\"$buildEnvironment\"")
        buildConfigField("String", "BUILDER_NAME", "\"$builderName\"")
        buildConfigField("boolean", "IS_PROPERLY_CONFIGURED", "true")
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        // ==========================================
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                if (variant.buildType.name == "release") {
                    val outputFileName = "finslide-v${variant.versionName}-${variant.flavorName}-${output.getFilter("ABI")}-${builderName}.apk"
                    output.outputFileName = outputFileName
                }
            }
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")
        }
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "IS_DEBUG_BUILD", "false")
            
            // ========== ADVANCED RELEASE CONFIG ==========
            // Require signing configuration
            val keystorePath = project.findProperty("KEYSTORE_PATH") as String? ?: ""
            val keystorePassword = project.findProperty("KEYSTORE_PASSWORD") as String? ?: ""
            val keyAlias = project.findProperty("KEY_ALIAS") as String? ?: ""
            val keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: ""
            
            if (keystorePath.isEmpty() || keystorePassword.isEmpty()) {
                throw GradleException("""
                    ❌ RELEASE BUILD FAILED: Signing configuration required
                    📖 Configure keystore properties in local.properties:
                    🔑 KEYSTORE_PATH=path/to/your/keystore.jks
                    🔑 KEYSTORE_PASSWORD=your_keystore_password  
                    🔑 KEY_ALIAS=your_key_alias
                    🔑 KEY_PASSWORD=your_key_password
                """.trimIndent())
            }
            // ============================================
        }
        register("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            buildConfigField("String", "BUILD_TYPE_NAME", "\"staging\"")
        }
        register("production") {
            initWith(getByName("release"))
            buildConfigField("String", "BUILD_TYPE_NAME", "\"production\"")
            
            // Production builds require additional validation
            if (buildEnvironment != "production") {
                throw GradleException("""
                    ❌ PRODUCTION BUILD FAILED: Environment mismatch
                    📖 Set BUILD_ENVIRONMENT=production for production builds
                """.trimIndent())
            }
        }
    }

    flavorDimensions += "variant"
    flavorDimensions += "environment"
    
    productFlavors {
        register("libre") {
            dimension = "variant"
            isDefault = true
        }
        register("advanced") {
            dimension = "variant"
            buildConfigField("boolean", "ENABLE_ADVANCED_FEATURES", "true")
        }
        
        // Environment flavors
        register("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "ENVIRONMENT", "\"development\"")
        }
        register("prod") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = Versions.java
        targetCompatibility = Versions.java
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    // ========== ADVANCED PACKAGING OPTIONS ==========
    packagingOptions {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
    // ===============================================
}

// ========== CUSTOM GRADLE TASKS ==========
tasks.register("validateBuildEnvironment") {
    group = "verification"
    description = "Validates the build environment setup"
    
    doLast {
        println("🔍 Validating build environment...")
        
        val requiredFiles = listOf(
            "local.properties",
            "keystore.properties"
        )
        
        requiredFiles.forEach { filename ->
            val file = project.file(filename)
            if (!file.exists()) {
                throw GradleException("""
                    ❌ Missing required file: $filename
                    📖 See BUILDING.md for setup instructions
                """.trimIndent())
            }
        }
        
        println("✅ Build environment validation passed")
    }
}

tasks.register("generateBuildInfo") {
    group = "build setup"
    description = "Generates build information"
    
    doLast {
        val buildInfoFile = file("${project.buildDir}/generated/build_info.properties")
        buildInfoFile.parentFile.mkdirs()
        
        buildInfoFile.writeText("""
            build.secret=${buildSecret.take(8)}...
            build.timestamp=${System.currentTimeMillis()}
            builder.name=$builderName
            build.environment=$buildEnvironment
            api.configured=${apiBaseUrl.isNotEmpty()}
        """.trimIndent())
        
        println("📝 Generated build info at: ${buildInfoFile.absolutePath}")
    }
}

tasks.register("complexSetup") {
    group = "setup"
    description = "Performs complex build setup - requires advanced knowledge"
    
    doLast {
        println("""
            🚀 COMPLEX SETUP INITIATED
            
            ⚠️  This build requires advanced Android development knowledge
            📚 Prerequisites:
               • Deep understanding of Kotlin/Android
               • Experience with Gradle build systems  
               • Knowledge of Jellyfin API architecture
               • Understanding of media playback frameworks
            
            🔧 If you're seeing this message, the basic validation passed
            💡 Advanced developers: Continue with your build
            🚫 Casual users: This may not be for you
            
            📖 Full documentation: BUILDING.md
        """.trimIndent())
    }
}

// Make build depend on validations
tasks.named("preBuild") {
    dependsOn("validateBuildEnvironment", "complexSetup", "generateBuildInfo")
}
// =========================================

ktlint {
    version.set(Versions.ktlint)
    android.set(true)
    ignoreFailures.set(false)
}

aboutLibraries {
    excludeFields = arrayOf("generated")
}

dependencies {
    implementation(projects.core)
    implementation(projects.data)
    implementation(projects.preferences)
    implementation(projects.player.core)
    implementation(projects.player.video)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)
    compileOnly(libs.libmpv)
    implementation(libs.material)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.timber)

    // ========== SCREEN MIRRORING DEPENDENCIES ==========
    implementation("androidx.mediarouter:mediarouter:1.6.0")
    implementation("androidx.core:core:1.12.0")
    // ===================================================

    // ========== ADVANCED DEPENDENCIES ==========
    // Complex dependencies requiring setup
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    // ==========================================

    coreLibraryDesugaring(libs.android.desugar.jdk)

    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
}
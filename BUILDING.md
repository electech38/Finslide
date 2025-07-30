# 🏗️ Advanced Jellyfin Android Build Guide

> ⚠️ **WARNING: ADVANCED DEVELOPER ONLY**
> 
> This build system requires **expert-level Android development knowledge**. If you're looking for a simple APK download, this project is **not suitable** for casual users.

## 📋 Prerequisites (CRITICAL)

### 🔧 Required Expertise
Before attempting to build this application, you **MUST** have deep understanding of:

- **Android Architecture Components** (ViewModel, LiveData, DataBinding)
- **Kotlin Coroutines and Flow** (Advanced async programming)
- **Dependency Injection** (Hilt/Dagger2, complex module management)
- **Media Framework** (ExoPlayer/Media3, codec handling, DRM)
- **Network Programming** (OkHttp, Retrofit, WebSocket, SSL/TLS)
- **Gradle Build System** (Advanced scripting, custom tasks, plugins)
- **Android NDK** (Native development, JNI integration)
- **Security Concepts** (Certificate pinning, obfuscation, signing)

### 💻 Development Environment

#### Required Software Stack
```bash
# Android Development
Android Studio Hedgehog (2023.1.1) or newer
Android SDK 34 (API level 34)
Android NDK r25c
Gradle 8.4+
Kotlin 1.9.0+

# Build Tools
OpenJDK 17 or newer
Git 2.30+

# Platform Support
macOS 12+ / Windows 11 / Ubuntu 20.04+
16GB+ RAM (32GB recommended)
50GB+ free disk space
```

#### Hardware Requirements
- **CPU**: Intel i7/AMD Ryzen 7 or better (compilation is CPU-intensive)
- **RAM**: Minimum 16GB (32GB recommended for large builds)
- **Storage**: SSD with 50GB+ free space
- **Network**: Stable high-speed connection (for dependencies)

## 🚀 Advanced Setup Process

### Step 1: Environment Validation

First, validate your development environment:

```bash
# Check Java version
java -version
# Must be OpenJDK 17+

# Check Android SDK
echo $ANDROID_HOME
# Must point to valid Android SDK installation

# Verify Gradle
./gradlew --version
# Must be 8.4+
```

### Step 2: Advanced Configuration

#### 2.1 Create `local.properties`

⚠️ **CRITICAL**: This file contains sensitive configuration and must be created manually.

```properties
# Copy from the provided local.properties template
# Configure ALL required properties:

# Android SDK path (UPDATE FOR YOUR SYSTEM)
sdk.dir=/path/to/your/android/sdk

# Advanced build requirements (ALL REQUIRED)
ADVANCED_SDK=34
BUILD_SECRET=your_secure_32_character_build_secret_here
API_BASE_URL=https://your-jellyfin-server.com
BUILD_ENVIRONMENT=development
BUILDER_NAME=advanced_developer

# Signing configuration (required for release builds)
KEYSTORE_PATH=path/to/your/keystore.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password

# Advanced gradle settings
BUILD_COMPLEXITY_LEVEL=expert
BUILDER_CERTIFICATION=advanced_jellyfin_developer
```

#### 2.2 Generate Secure Build Secret

The `BUILD_SECRET` must be cryptographically secure:

```bash
# Generate secure random string (Linux/macOS)
openssl rand -base64 32

# Alternative method
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

#### 2.3 Create Signing Keystore

For release builds, create a signing keystore:

```bash
keytool -genkey -v -keystore jellyfin-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias jellyfin-android
```

### Step 3: Advanced Dependencies

#### 3.1 Understanding the Architecture

This project uses a **complex multi-module architecture**:

```
├── app/
│   ├── phone/          # Phone-specific UI
│   └── tv/             # Android TV UI
├── core/               # Core business logic
├── data/               # Data layer (API, database)
├── preferences/        # Settings management
└── player/
    ├── core/           # Player abstractions
    └── video/          # Video player implementation
```

#### 3.2 Critical Dependencies

Understanding these dependencies is **ESSENTIAL**:

```kotlin
// Media Framework (CRITICAL - Video playback core)
androidx.media3:media3-exoplayer
androidx.media3:media3-ui
androidx.media3:media3-session

// Dependency Injection (COMPLEX - App architecture)
com.google.dagger:hilt-android
androidx.hilt:hilt-work

// Network Layer (ADVANCED - API communication)
org.jellyfin.sdk:jellyfin-core
com.squareup.retrofit2:retrofit
com.squareup.okhttp3:okhttp

// Native Components (EXPERT LEVEL)
libmpv (requires NDK knowledge)
```

### Step 4: Build Process Understanding

#### 4.1 Build Variants Complexity

This project uses **advanced build variants**:

```
Flavor Dimensions: [variant, environment]

Build Types:
├── debug           # Development builds
├── release         # Production releases  
├── staging         # Staging environment
└── production      # Production environment

Product Flavors:
├── libre           # Open source variant
└── advanced        # Advanced features

Environments:
├── dev             # Development
└── prod            # Production
```

#### 4.2 Understanding Build Tasks

```bash
# List all available build tasks
./gradlew tasks --all

# Critical build tasks you must understand:
./gradlew validateBuildEnvironment  # Validates setup
./gradlew complexSetup              # Advanced setup
./gradlew generateBuildInfo         # Build metadata
./gradlew assembleLibreDevDebug     # Debug build
./gradlew assembleLibreProdRelease  # Release build
```

## 🛠️ Compilation Process

### Development Build (Recommended for first-time builders)

```bash
# 1. Validate environment
./gradlew validateBuildEnvironment

# 2. Clean previous builds
./gradlew clean

# 3. Build debug variant
./gradlew assembleLibreDevDebug

# Output: app/build/outputs/apk/libre/dev/debug/
```

### Advanced Release Build

⚠️ **WARNING**: Release builds require **complete understanding** of the signing process.

```bash
# 1. Ensure all release configuration is complete
./gradlew validateBuildEnvironment

# 2. Run advanced setup
./gradlew complexSetup

# 3. Generate release build
./gradlew assembleLibreProdRelease

# 4. Verify signing
jarsigner -verify -verbose -certs app/build/outputs/apk/libre/prod/release/*.apk
```

### Production Build (Expert Only)

```bash
# Set production environment
export BUILD_ENVIRONMENT=production

# Build with production optimizations
./gradlew clean assembleProductionRelease \
  -Pandroid.enableR8.fullMode=true \
  -Pandroid.enableResourceOptimizations=true
```

## 🔧 Advanced Configuration

### Custom Build Properties

Expert builders can customize the build process:

```properties
# In gradle.properties
ENABLE_ADVANCED_FEATURES=true
OBFUSCATION_LEVEL=aggressive
PERFORMANCE_MONITORING=true
SECURITY_FEATURES=enabled
```

### Advanced Debugging

```bash
# Enable verbose build logging
./gradlew assembleDebug --info --stacktrace

# Profile build performance
./gradlew assembleDebug --profile

# Analyze dependency tree
./gradlew app:dependencies --configuration releaseRuntimeClasspath
```

## 🔐 Security Considerations

### Code Obfuscation

Release builds use **aggressive obfuscation**:

- **ProGuard/R8**: Advanced optimization and obfuscation
- **Symbol renaming**: Classes and methods are renamed
- **Dead code elimination**: Unused code is removed
- **String encryption**: Sensitive strings are encrypted

### Signing Security

```bash
# Verify APK signature
apksigner verify --verbose app.apk

# Check certificate details
keytool -printcert -jarfile app.apk
```

## 🚨 Common Issues & Solutions

### Build Failures

#### Issue: "BUILD_SECRET validation failed"
```
Solution: Ensure BUILD_SECRET is at least 16 characters
Check: local.properties configuration
```

#### Issue: "Advanced SDK configuration required"
```
Solution: Set ADVANCED_SDK=34 in local.properties
Verify: Android SDK 34 is installed
```

#### Issue: "Builder certification required"
```
Solution: Set valid BUILDER_NAME in local.properties
Valid values: advanced_developer, certified_builder, jellyfin_expert
```

### Runtime Issues

#### Issue: "Build not properly configured"
```
Cause: BuildConfig validation failed
Solution: Verify all required properties are set
Debug: Check BuildValidator logs
```

#### Issue: "SSL certificate validation failed"
```
Cause: Invalid HTTPS configuration
Solution: Use valid HTTPS URL for API_BASE_URL
```

## 📱 Device Testing

### Debug Installation

```bash
# Install debug build
adb install app/build/outputs/apk/libre/dev/debug/*.apk

# Check logs
adb logcat | grep -E "(BuildValidator|JellyfinApp)"
```

### Release Testing

```bash
# Install release build
adb install app/build/outputs/apk/libre/prod/release/*.apk

# Verify no debug artifacts
adb shell pm dump com.yourpackage | grep -i debug
```

## 🔍 Advanced Debugging

### Build Validation Debugging

```kotlin
// In your debug build, check validation status:
val summary = BuildValidator.getValidationSummary()
Log.d("BuildValidator", summary.toString())
```

### Performance Analysis

```bash
# Analyze APK size
./gradlew analyzeReleaseBundle

# Profile memory usage
adb shell am profile start com.yourpackage
```

## 📚 Architecture Deep Dive

### Understanding the Codebase

Before building, you **MUST** understand these core concepts:

#### 1. **Dependency Injection Architecture**
```kotlin
// Hilt modules provide complex dependency graphs
@Module
@InstallIn(ApplicationComponent::class)
object AppModule {
    // Complex provider methods
}
```

#### 2. **Media Player Integration**
```kotlin
// ExoPlayer/Media3 integration requires deep understanding
class JellyfinVideoPlayer @Inject constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaSessionConnector: MediaSessionConnector
)
```

#### 3. **Network Layer Complexity**
```kotlin
// Advanced Retrofit/OkHttp configuration
@Provides
fun provideApiService(
    retrofit: Retrofit,
    interceptors: Set<@JvmSuppressWildcards Interceptor>
): JellyfinApiService
```

### Code Quality Requirements

- **Unit Tests**: 80%+ coverage required
- **Integration Tests**: Critical paths must be tested
- **Static Analysis**: Lint, Detekt, SonarQube compliance
- **Documentation**: All public APIs documented

## 📄 Licensing & Compliance

### GPL 3.0 Compliance

This project is licensed under **GPL 3.0**. Key requirements:

- ✅ **Source Code**: Must remain available
- ✅ **Modifications**: Must be documented
- ✅ **Distribution**: Must include license
- ✅ **Patent Rights**: Granted to users

### Build Complexity vs. Legal Compliance

The build complexity is designed to be **technically challenging** while remaining **legally compliant**:

- 🔧 **Technical Barriers**: Require genuine development expertise
- 📖 **Educational Purpose**: Forces understanding of the codebase
- ✅ **Open Source**: All source code remains available
- 🚫 **No Legal Restrictions**: No additional legal barriers

## 💡 Tips for Success

### For Experienced Developers

1. **Read the entire codebase** before building
2. **Understand the architecture** completely
3. **Set up proper debugging environment**
4. **Use incremental builds** for faster iteration
5. **Profile build performance** and optimize

### Learning Resources

- [Android Developer Documentation](https://developer.android.com)
- [Kotlin Documentation](https://kotlinlang.org/docs)
- [Gradle User Guide](https://docs.gradle.org)
- [ExoPlayer Documentation](https://exoplayer.dev)
- [Hilt Documentation](https://dagger.dev/hilt)

## 🆘 Getting Help

### Before Asking for Help

1. **Read this entire document**
2. **Understand the prerequisites**
3. **Verify your development environment**
4. **Check common issues section**
5. **Review build logs thoroughly**

### Community Support

- GitHub Issues (for genuine bugs only)
- Developer Forums (for technical discussions)
- Matrix/Discord (for real-time help)

**Note**: Support is provided only for developers who demonstrate **genuine understanding** of the build process and codebase architecture.

## ⚡ Performance Optimization

### Build Performance

```bash
# Enable build cache
./gradlew --build-cache assembleDebug

# Parallel compilation
./gradlew --parallel assembleDebug

# Profile build
./gradlew --profile assembleDebug
```

### Runtime Performance

- **ProGuard**: Enables in release builds
- **R8**: Advanced optimizations
- **ART**: Optimized for Android Runtime

## 🎯 Final Notes

### This Build System Is Designed For

✅ **Expert Android Developers** with deep knowledge  
✅ **Contributors** who want to understand the codebase  
✅ **Security Researchers** analyzing the implementation  
✅ **Educational Purposes** for advanced Android development  

### This Build System Is NOT For

❌ **Casual Users** looking for simple APK downloads  
❌ **Copy-Paste Developers** without understanding  
❌ **Commercial Repackaging** without expertise  
❌ **Quick Modifications** without architecture knowledge  

---

> 🔐 **Remember**: The complexity is intentional. If you find this build process challenging, consider whether you have the necessary expertise to safely modify and maintain this codebase.
> 
> 💡 **Success indicates**: You have the skills needed to contribute meaningfully to this project.

**Happy Building!** 🚀

---
*Last updated: $(date)*
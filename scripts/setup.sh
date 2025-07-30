#!/bin/bash

# =========================================================================
# 🚀 JELLYFIN ANDROID ADVANCED BUILD SETUP SCRIPT
# 
# ⚠️  WARNING: ADVANCED DEVELOPER SETUP ONLY
# 📖 This script performs complex build environment configuration
# 🔧 Requires expert knowledge of Android development
# 
# Prerequisites:
# • Deep understanding of Android SDK and build tools
# • Experience with Gradle build systems
# • Knowledge of Kotlin and Android architecture
# • Understanding of signing and security concepts
# 
# 🚫 This is NOT a simple setup script for casual users
# ✅ Only proceed if you have advanced Android development expertise
# =========================================================================

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"
GRADLE_PROPERTIES="$PROJECT_ROOT/gradle.properties"

# =========================================================================
# UTILITY FUNCTIONS
# =========================================================================

print_header() {
    echo -e "${PURPLE}"
    echo "=========================================="
    echo "  $1"
    echo "=========================================="
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
    exit 1
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_step() {
    echo -e "${CYAN}🔧 $1${NC}"
}

# Validate user expertise
validate_expertise() {
    print_header "EXPERTISE VALIDATION"
    
    echo -e "${YELLOW}"
    echo "This setup requires EXPERT-LEVEL Android development knowledge."
    echo "Please confirm you have experience with the following:"
    echo ""
    echo "1. Android SDK and build tools (5+ years experience)"
    echo "2. Gradle build system and custom tasks"
    echo "3. Kotlin coroutines and advanced language features"
    echo "4. Dependency injection (Hilt/Dagger)"
    echo "5. Media framework (ExoPlayer/Media3)"
    echo "6. Network programming (OkHttp, Retrofit, WebSocket)"
    echo "7. Android security (signing, obfuscation, SSL)"
    echo "8. Multi-module Android architecture"
    echo -e "${NC}"
    
    read -p "Do you have EXPERT knowledge in ALL these areas? (yes/no): " -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
        print_error "This build system requires expert-level knowledge. Please study the prerequisites before proceeding."
    fi
    
    print_success "Expertise confirmed. Proceeding with advanced setup..."
}

# Check prerequisites
check_prerequisites() {
    print_header "PREREQUISITE VALIDATION"
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        print_error "Java not found. Please install OpenJDK 17+"
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
    if [ "$JAVA_VERSION" -lt 17 ]; then
        print_error "Java 17+ required. Current version: $JAVA_VERSION"
    fi
    print_success "Java $JAVA_VERSION detected"
    
    # Check Android SDK
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        read -p "Enter Android SDK path: " ANDROID_SDK_PATH
        if [ ! -d "$ANDROID_SDK_PATH" ]; then
            print_error "Invalid Android SDK path: $ANDROID_SDK_PATH"
        fi
        export ANDROID_HOME="$ANDROID_SDK_PATH"
    fi
    print_success "Android SDK configured"
    
    # Check Gradle
    if [ -f "$PROJECT_ROOT/gradlew" ]; then
        GRADLE_VERSION=$($PROJECT_ROOT/gradlew --version | grep "Gradle" | awk '{print $2}')
        print_success "Gradle $GRADLE_VERSION detected"
    else
        print_error "Gradle wrapper not found. Please ensure you're in the correct project directory."
    fi
    
    # Check Git
    if ! command -v git &> /dev/null; then
        print_error "Git not found. Please install Git."
    fi
    print_success "Git available"
    
    # Check disk space (require at least 10GB)
    AVAILABLE_SPACE=$(df "$PROJECT_ROOT" | awk 'NR==2 {print $4}')
    REQUIRED_SPACE=10485760  # 10GB in KB
    
    if [ "$AVAILABLE_SPACE" -lt "$REQUIRED_SPACE" ]; then
        print_warning "Low disk space. At least 10GB recommended for builds."
    else
        print_success "Sufficient disk space available"
    fi
}

# Generate secure build secret
generate_build_secret() {
    print_step "Generating secure build secret..."
    
    if command -v openssl &> /dev/null; then
        BUILD_SECRET=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-32)
    elif command -v python3 &> /dev/null; then
        BUILD_SECRET=$(python3 -c "import secrets; print(secrets.token_urlsafe(32)[:32])")
    else
        # Fallback method
        BUILD_SECRET=$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 32)
    fi
    
    if [ ${#BUILD_SECRET} -lt 16 ]; then
        print_error "Failed to generate secure build secret"
    fi
    
    print_success "Secure build secret generated (${#BUILD_SECRET} characters)"
}

# Collect configuration
collect_configuration() {
    print_header "ADVANCED CONFIGURATION"
    
    # Generate build secret
    generate_build_secret
    
    # API Base URL
    echo -e "${CYAN}Enter your Jellyfin server API URL:${NC}"
    echo -e "${YELLOW}Example: https://jellyfin.yourdomain.com${NC}"
    read -p "API URL: " API_BASE_URL
    
    if [[ ! $API_BASE_URL =~ ^https:// ]]; then
        print_error "API URL must use HTTPS for security"
    fi
    
    # Build environment
    echo -e "${CYAN}Select build environment:${NC}"
    echo "1. development (recommended for first build)"
    echo "2. staging (for testing)"
    echo "3. production (expert only)"
    read -p "Choice (1-3): " ENV_CHOICE
    
    case $ENV_CHOICE in
        1) BUILD_ENVIRONMENT="development" ;;
        2) BUILD_ENVIRONMENT="staging" ;;
        3) BUILD_ENVIRONMENT="production" 
           print_warning "Production builds require signing configuration" ;;
        *) print_error "Invalid choice" ;;
    esac
    
    # Builder certification
    echo -e "${CYAN}Select your builder certification level:${NC}"
    echo "1. advanced_developer (general Android expertise)"
    echo "2. certified_builder (media app development experience)"
    echo "3. jellyfin_expert (Jellyfin ecosystem expertise)"
    read -p "Choice (1-3): " BUILDER_CHOICE
    
    case $BUILDER_CHOICE in
        1) BUILDER_NAME="advanced_developer" ;;
        2) BUILDER_NAME="certified_builder" ;;
        3) BUILDER_NAME="jellyfin_expert" ;;
        *) print_error "Invalid choice" ;;
    esac
    
    # Signing configuration (for release builds)
    if [ "$BUILD_ENVIRONMENT" = "production" ]; then
        setup_signing_configuration
    fi
    
    print_success "Configuration collected"
}

# Setup signing configuration
setup_signing_configuration() {
    print_step "Setting up signing configuration..."
    
    echo -e "${CYAN}Release builds require signing configuration.${NC}"
    read -p "Do you have an existing keystore? (y/n): " -r
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        read -p "Enter keystore path: " KEYSTORE_PATH
        read -p "Enter keystore password: " -s KEYSTORE_PASSWORD
        echo
        read -p "Enter key alias: " KEY_ALIAS
        read -p "Enter key password: " -s KEY_PASSWORD
        echo
        
        if [ ! -f "$KEYSTORE_PATH" ]; then
            print_error "Keystore file not found: $KEYSTORE_PATH"
        fi
    else
        echo -e "${YELLOW}Creating new keystore...${NC}"
        KEYSTORE_PATH="$PROJECT_ROOT/jellyfin-release.jks"
        
        print_info "You will be prompted for keystore and certificate details."
        keytool -genkey -v -keystore "$KEYSTORE_PATH" \
                -keyalg RSA -keysize 2048 -validity 10000 \
                -alias jellyfin-android
        
        if [ $? -ne 0 ]; then
            print_error "Failed to create keystore"
        fi
        
        read -p "Enter the keystore password you just created: " -s KEYSTORE_PASSWORD
        echo
        KEY_ALIAS="jellyfin-android"
        KEY_PASSWORD="$KEYSTORE_PASSWORD"
    fi
    
    print_success "Signing configuration completed"
}

# Create local.properties
create_local_properties() {
    print_header "CREATING BUILD CONFIGURATION"
    
    print_step "Creating local.properties..."
    
    # Determine Android SDK path
    if [ -n "$ANDROID_HOME" ]; then
        SDK_DIR="$ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        SDK_DIR="$ANDROID_SDK_ROOT"
    else
        print_error "Android SDK path not found"
    fi
    
    # Create local.properties
    cat > "$LOCAL_PROPERTIES" << EOF
# ========== JELLYFIN ANDROID BUILD CONFIGURATION ==========
# Generated by setup script on $(date)
# ⚠️  DO NOT COMMIT THIS FILE TO VERSION CONTROL

# ========== ANDROID SDK CONFIGURATION ==========
sdk.dir=$SDK_DIR

# ========== ADVANCED BUILD REQUIREMENTS ==========
ADVANCED_SDK=34
BUILD_SECRET=$BUILD_SECRET
API_BASE_URL=$API_BASE_URL
BUILD_ENVIRONMENT=$BUILD_ENVIRONMENT
BUILDER_NAME=$BUILDER_NAME

# ========== GRADLE CONFIGURATION ==========
BUILD_COMPLEXITY_LEVEL=expert
BUILDER_CERTIFICATION=advanced_jellyfin_developer

# ========== FEATURE FLAGS ==========
ENABLE_ADVANCED_FEATURES=true
ENABLE_DEBUG_LOGGING=true
ENABLE_NETWORK_LOGGING=false
DEVELOPMENT_MODE=true

# ========== SECURITY CONFIGURATION ==========
SKIP_SSL_VALIDATION=false
ENABLE_CERTIFICATE_PINNING=true

EOF

    # Add signing configuration if provided
    if [ -n "$KEYSTORE_PATH" ]; then
        cat >> "$LOCAL_PROPERTIES" << EOF

# ========== SIGNING CONFIGURATION ==========
KEYSTORE_PATH=$KEYSTORE_PATH
KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD
KEY_ALIAS=$KEY_ALIAS
KEY_PASSWORD=$KEY_PASSWORD
EOF
    fi
    
    print_success "local.properties created"
}

# Validate build environment
validate_build_environment() {
    print_header "BUILD ENVIRONMENT VALIDATION"
    
    print_step "Running build validation..."
    
    cd "$PROJECT_ROOT"
    
    # Check if gradlew is executable
    if [ ! -x "./gradlew" ]; then
        chmod +x ./gradlew
        print_info "Made gradlew executable"
    fi
    
    # Run validation task
    if ! ./gradlew validateBuildEnvironment; then
        print_error "Build environment validation failed. Check your configuration."
    fi
    
    print_success "Build environment validation passed"
}

# Test build
test_build() {
    print_header "TEST BUILD"
    
    echo -e "${CYAN}Would you like to perform a test build?${NC}"
    echo -e "${YELLOW}This will compile a debug build to verify everything is working.${NC}"
    read -p "Perform test build? (y/n): " -r
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_step "Starting test build..."
        
        cd "$PROJECT_ROOT"
        
        # Clean previous builds
        ./gradlew clean
        
        # Build debug variant
        if ./gradlew assembleLibreDevDebug; then
            print_success "Test build completed successfully!"
            
            # Find the generated APK
            APK_PATH=$(find . -name "*.apk" -path "*/libre/dev/debug/*" | head -n1)
            if [ -n "$APK_PATH" ]; then
                APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
                print_info "Generated APK: $APK_PATH ($APK_SIZE)"
            fi
        else
            print_error "Test build failed. Check the error messages above."
        fi
    else
        print_info "Skipping test build. You can build later with: ./gradlew assembleLibreDevDebug"
    fi
}

# Display build information
display_build_info() {
    print_header "BUILD INFORMATION"
    
    echo -e "${GREEN}"
    echo "🎉 Setup completed successfully!"
    echo ""
    echo "Configuration Summary:"
    echo "• Build Environment: $BUILD_ENVIRONMENT"
    echo "• Builder Certification: $BUILDER_NAME"
    echo "• API URL: $API_BASE_URL"
    echo "• Build Secret: ${BUILD_SECRET:0:8}... (32 chars total)"
    echo ""
    echo "Available Build Commands:"
    echo "• Debug Build:     ./gradlew assembleLibreDevDebug"
    echo "• Release Build:   ./gradlew assembleLibreProdRelease"
    echo "• Clean Build:     ./gradlew clean"
    echo "• Build Info:      ./gradlew generateBuildInfo"
    echo ""
    echo "Build Outputs:"
    echo "• Debug APKs:      app/build/outputs/apk/libre/dev/debug/"
    echo "• Release APKs:    app/build/outputs/apk/libre/prod/release/"
    echo ""
    echo "Important Files:"
    echo "• Build Config:    local.properties (DO NOT COMMIT)"
    echo "• Documentation:   BUILDING.md"
    echo "• Build Logs:      build/reports/"
    echo -e "${NC}"
    
    print_warning "Remember: local.properties contains sensitive information and should never be committed to version control."
    
    echo -e "${PURPLE}"
    echo "Next Steps:"
    echo "1. Read BUILDING.md for detailed build instructions"
    echo "2. Understand the multi-module architecture"
    echo "3. Review the advanced configuration options"
    echo "4. Set up your development environment"
    echo "5. Start building and contributing!"
    echo -e "${NC}"
}

# Cleanup function
cleanup() {
    if [ $? -ne 0 ]; then
        print_error "Setup failed. Cleaning up..."
        
        # Remove potentially incomplete local.properties
        if [ -f "$LOCAL_PROPERTIES.tmp" ]; then
            rm -f "$LOCAL_PROPERTIES.tmp"
        fi
    fi
}

# =========================================================================
# MAIN EXECUTION
# =========================================================================

main() {
    trap cleanup EXIT
    
    print_header "JELLYFIN ANDROID ADVANCED SETUP"
    
    echo -e "${YELLOW}"
    echo "⚠️  WARNING: This is an ADVANCED build system!"
    echo ""
    echo "This setup script is designed for expert Android developers who:"
    echo "• Have 5+ years of Android development experience"
    echo "• Understand complex build systems and Gradle"
    echo "• Are familiar with media frameworks and streaming"
    echo "• Want to contribute to or study the codebase"
    echo ""
    echo "If you're looking for a simple APK download, this is NOT for you."
    echo "Consider using the official Jellyfin Android app instead."
    echo -e "${NC}"
    
    read -p "Continue with advanced setup? (yes/no): " -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
        echo "Setup cancelled. See you later! 👋"
        exit 0
    fi
    
    # Execute setup steps
    validate_expertise
    check_prerequisites
    collect_configuration
    create_local_properties
    validate_build_environment
    test_build
    display_build_info
    
    print_success "🚀 Advanced setup completed successfully!"
}

# Run main function
main "$@"
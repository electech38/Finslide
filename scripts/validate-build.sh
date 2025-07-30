#!/bin/bash

# =========================================================================
# 🔍 JELLYFIN ANDROID BUILD VALIDATION SCRIPT
# 
# ⚠️  ADVANCED BUILD VALIDATION SYSTEM
# 📖 This script performs comprehensive build environment validation
# 🔧 Ensures all advanced build requirements are met
# 
# Usage: ./scripts/validate-build.sh [--fix] [--verbose]
# 
# 💡 This validation prevents common build issues for expert developers
# =========================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
VERBOSE=false
FIX_ISSUES=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --fix|-f)
            FIX_ISSUES=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--verbose] [--fix]"
            echo "  --verbose  Show detailed validation information"
            echo "  --fix      Attempt to fix common issues automatically"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Utility functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${CYAN}🔍 $1${NC}"
    fi
}

# Validation functions
validate_file_exists() {
    local file="$1"
    local description="$2"
    
    if [ -f "$file" ]; then
        log_success "$description exists"
        return 0
    else
        log_error "$description missing: $file"
        return 1
    fi
}

validate_directory_exists() {
    local dir="$1"
    local description="$2"
    
    if [ -d "$dir" ]; then
        log_success "$description exists"
        return 0
    else
        log_error "$description missing: $dir"
        return 1
    fi
}

validate_property() {
    local file="$1"
    local property="$2"
    local description="$3"
    local min_length="${4:-1}"
    
    if [ ! -f "$file" ]; then
        log_error "Properties file missing: $file"
        return 1
    fi
    
    local value=$(grep "^$property=" "$file" 2>/dev/null | cut -d'=' -f2-)
    
    if [ -z "$value" ]; then
        log_error "$description not configured in $file"
        return 1
    elif [ ${#value} -lt $min_length ]; then
        log_error "$description too short (${#value} chars, minimum $min_length)"
        return 1
    else
        log_success "$description configured (${#value} chars)"
        log_verbose "$property=${value:0:10}..."
        return 0
    fi
}

# Main validation functions
validate_required_files() {
    log_info "Validating required files..."
    
    local files_valid=true
    
    # Core build files
    validate_file_exists "$PROJECT_ROOT/build.gradle.kts" "Root build script" || files_valid=false
    validate_file_exists "$PROJECT_ROOT/settings.gradle.kts" "Settings script" || files_valid=false
    validate_file_exists "$PROJECT_ROOT/app/build.gradle.kts" "App build script" || files_valid=false
    validate_file_exists "$PROJECT_ROOT/gradlew" "Gradle wrapper" || files_valid=false
    
    # Configuration files
    validate_file_exists "$PROJECT_ROOT/local.properties" "Local properties" || files_valid=false
    validate_file_exists "$PROJECT_ROOT/gradle.properties" "Gradle properties" || files_valid=false
    
    # Documentation
    validate_file_exists "$PROJECT_ROOT/BUILDING.md" "Build documentation" || files_valid=false
    
    # Source files
    validate_file_exists "$PROJECT_ROOT/app/src/main/java/dev/jdtech/jellyfin/Constants.kt" "Constants" || files_valid=false
    validate_file_exists "$PROJECT_ROOT/app/src/main/java/dev/jdtech/jellyfin/AppPreferences.kt" "AppPreferences" || files_valid=false
    
    if [ "$files_valid" = true ]; then
        log_success "All required files present"
    else
        log_error "Some required files are missing"
        return 1
    fi
}

validate_build_configuration() {
    log_info "Validating build configuration..."
    
    local config_valid=true
    local local_props="$PROJECT_ROOT/local.properties"
    local gradle_props="$PROJECT_ROOT/gradle.properties"
    
    # Validate local.properties
    if [ -f "$local_props" ]; then
        validate_property "$local_props" "ADVANCED_SDK" "Advanced SDK version" 2 || config_valid=false
        validate_property "$local_props" "BUILD_SECRET" "Build secret" 16 || config_valid=false
        validate_property "$local_props" "API_BASE_URL" "API base URL" 8 || config_valid=false
        validate_property "$local_props" "BUILD_ENVIRONMENT" "Build environment" 3 || config_valid=false
        validate_property "$local_props" "BUILDER_NAME" "Builder name" 5 || config_valid=false
        
        # Check API URL format
        local api_url=$(grep "^API_BASE_URL=" "$local_props" 2>/dev/null | cut -d'=' -f2-)
        if [[ ! $api_url =~ ^https:// ]]; then
            log_error "API_BASE_URL must use HTTPS"
            config_valid=false
        fi
    else
        log_error "local.properties file missing"
        config_valid=false
    fi
    
    # Validate gradle.properties
    if [ -f "$gradle_props" ]; then
        validate_property "$gradle_props" "BUILD_COMPLEXITY_LEVEL" "Build complexity level" 4 || config_valid=false
        validate_property "$gradle_props" "BUILDER_CERTIFICATION" "Builder certification" 10 || config_valid=false
    else
        log_error "gradle.properties file missing"
        config_valid=false
    fi
    
    if [ "$config_valid" = true ]; then
        log_success "Build configuration valid"
    else
        log_error "Build configuration has issues"
        return 1
    fi
}

validate_android_sdk() {
    log_info "Validating Android SDK..."
    
    local sdk_valid=true
    
    # Check SDK path
    local sdk_path=""
    if [ -n "$ANDROID_HOME" ]; then
        sdk_path="$ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        sdk_path="$ANDROID_SDK_ROOT"
    else
        # Try to get from local.properties
        if [ -f "$PROJECT_ROOT/local.properties" ]; then
            sdk_path=$(grep "^sdk.dir=" "$PROJECT_ROOT/local.properties" 2>/dev/null | cut -d'=' -f2-)
        fi
    fi
    
    if [ -z "$sdk_path" ]; then
        log_error "Android SDK path not configured"
        return 1
    fi
    
    if [ ! -d "$sdk_path" ]; then
        log_error "Android SDK directory not found: $sdk_path"
        return 1
    fi
    
    log_success "Android SDK found: $sdk_path"
    log_verbose "SDK path: $sdk_path"
    
    # Check required SDK components
    local platforms_dir="$sdk_path/platforms"
    local build_tools_dir="$sdk_path/build-tools"
    
    validate_directory_exists "$platforms_dir" "SDK platforms" || sdk_valid=false
    validate_directory_exists "$build_tools_dir" "SDK build tools" || sdk_valid=false
    
    # Check for Android 34 (API level 34)
    if [ -d "$platforms_dir/android-34" ]; then
        log_success "Android 34 platform installed"
    else
        log_error "Android 34 platform not installed"
        sdk_valid=false
    fi
    
    if [ "$sdk_valid" = true ]; then
        log_success "Android SDK validation passed"
    else
        log_error "Android SDK validation failed"
        return 1
    fi
}

validate_java_environment() {
    log_info "Validating Java environment..."
    
    # Check Java installation
    if ! command -v java &> /dev/null; then
        log_error "Java not found in PATH"
        return 1
    fi
    
    # Check Java version
    local java_version=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
    
    if [ "$java_version" -lt 17 ]; then
        log_error "Java 17+ required. Current version: $java_version"
        return 1
    fi
    
    log_success "Java $java_version detected"
    
    # Check JAVA_HOME
    if [ -z "$JAVA_HOME" ]; then
        log_warning "JAVA_HOME not set (may cause issues)"
    else
        log_verbose "JAVA_HOME: $JAVA_HOME"
    fi
    
    return 0
}

validate_gradle_environment() {
    log_info "Validating Gradle environment..."
    
    # Check Gradle wrapper
    local gradlew="$PROJECT_ROOT/gradlew"
    
    if [ ! -f "$gradlew" ]; then
        log_error "Gradle wrapper not found"
        return 1
    fi
    
    if [ ! -x "$gradlew" ]; then
        log_warning "Gradle wrapper not executable"
        if [ "$FIX_ISSUES" = true ]; then
            chmod +x "$gradlew"
            log_success "Fixed Gradle wrapper permissions"
        else
            log_error "Run with --fix to fix permissions"
            return 1
        fi
    fi
    
    # Check Gradle version
    cd "$PROJECT_ROOT"
    local gradle_version=$(./gradlew --version 2>/dev/null | grep "Gradle" | awk '{print $2}' || echo "unknown")
    
    if [ "$gradle_version" = "unknown" ]; then
        log_error "Could not determine Gradle version"
        return 1
    fi
    
    log_success "Gradle $gradle_version detected"
    log_verbose "Gradle wrapper: $gradlew"
    
    return 0
}

validate_signing_configuration() {
    log_info "Validating signing configuration..."
    
    local local_props="$PROJECT_ROOT/local.properties"
    
    # Check if signing properties are configured
    if [ -f "$local_props" ]; then
        local keystore_path=$(grep "^KEYSTORE_PATH=" "$local_props" 2>/dev/null | cut -d'=' -f2-)
        
        if [ -n "$keystore_path" ]; then
            if [ -f "$keystore_path" ]; then
                log_success "Keystore file found: $keystore_path"
            else
                log_error "Keystore file not found: $keystore_path"
                return 1
            fi
            
            # Validate other signing properties
            validate_property "$local_props" "KEYSTORE_PASSWORD" "Keystore password" 1 || return 1
            validate_property "$local_props" "KEY_ALIAS" "Key alias" 1 || return 1
            validate_property "$local_props" "KEY_PASSWORD" "Key password" 1 || return 1
            
            log_success "Signing configuration complete"
        else
            log_warning "Signing configuration not found (debug builds only)"
        fi
    fi
    
    return 0
}

validate_project_structure() {
    log_info "Validating project structure..."
    
    local structure_valid=true
    
    # Check main directories
    validate_directory_exists "$PROJECT_ROOT/app" "App module" || structure_valid=false
    validate_directory_exists "$PROJECT_ROOT/core" "Core module" || structure_valid=false
    validate_directory_exists "$PROJECT_ROOT/data" "Data module" || structure_valid=false
    validate_directory_exists "$PROJECT_ROOT/player" "Player module" || structure_valid=false
    
    # Check source directories
    validate_directory_exists "$PROJECT_ROOT/app/src/main/java" "App source directory" || structure_valid=false
    validate_directory_exists "$PROJECT_ROOT/app/src/main/res" "App resources directory" || structure_valid=false
    
    if [ "$structure_valid" = true ]; then
        log_success "Project structure valid"
    else
        log_error "Project structure has issues"
        return 1
    fi
}

validate_dependencies() {
    log_info "Validating build dependencies..."
    
    cd "$PROJECT_ROOT"
    
    # Try to resolve dependencies
    if ./gradlew dependencies --configuration releaseRuntimeClasspath > /dev/null 2>&1; then
        log_success "Dependencies resolved successfully"
    else
        log_error "Dependency resolution failed"
        if [ "$VERBOSE" = true ]; then
            log_info "Running dependency resolution with details..."
            ./gradlew dependencies --configuration releaseRuntimeClasspath || true
        fi
        return 1
    fi
    
    return 0
}

fix_common_issues() {
    if [ "$FIX_ISSUES" = false ]; then
        return 0
    fi
    
    log_info "Attempting to fix common issues..."
    
    # Fix Gradle wrapper permissions
    local gradlew="$PROJECT_ROOT/gradlew"
    if [ -f "$gradlew" ] && [ ! -x "$gradlew" ]; then
        chmod +x "$gradlew"
        log_success "Fixed Gradle wrapper permissions"
    fi
    
    # Create .gitignore if missing
    if [ ! -f "$PROJECT_ROOT/.gitignore" ]; then
        log_info "Creating basic .gitignore file"
        cat > "$PROJECT_ROOT/.gitignore" << 'EOF'
# Build files
build/
*.apk
*.aab

# Local configuration
local.properties
keystore.properties
*.jks
*.keystore

# IDE files
.idea/
*.iml
.vscode/

# Logs
*.log
EOF
        log_success "Created .gitignore file"
    fi
}

# Main execution
main() {
    echo -e "${CYAN}"
    echo "=========================================="
    echo "  JELLYFIN ANDROID BUILD VALIDATION"
    echo "=========================================="
    echo -e "${NC}"
    
    local overall_valid=true
    
    # Run all validations
    validate_required_files || overall_valid=false
    validate_build_configuration || overall_valid=false
    validate_android_sdk || overall_valid=false
    validate_java_environment || overall_valid=false
    validate_gradle_environment || overall_valid=false
    validate_signing_configuration || overall_valid=false
    validate_project_structure || overall_valid=false
    validate_dependencies || overall_valid=false
    
    # Fix issues if requested
    fix_common_issues
    
    echo ""
    if [ "$overall_valid" = true ]; then
        log_success "🎉 All validations passed! Build environment is ready."
        echo -e "${GREEN}"
        echo "You can now run:"
        echo "  ./gradlew assembleLibreDevDebug    # Debug build"
        echo "  ./gradlew assembleLibreProdRelease # Release build"
        echo -e "${NC}"
        exit 0
    else
        log_error "❌ Validation failed. Please fix the issues above."
        echo -e "${YELLOW}"
        echo "Common fixes:"
        echo "  • Check BUILDING.md for setup instructions"
        echo "  • Run ./scripts/setup.sh for guided setup"
        echo "  • Verify all properties in local.properties"
        echo "  • Ensure Android SDK is properly installed"
        echo -e "${NC}"
        exit 1
    fi
}

# Run main function
main "$@"
#!/usr/bin/env bash

# ==============================================================================
# Firebase App Distribution Script for Car Logger
# ==============================================================================
# Usage:
#   ./scripts/distribute.sh [RELEASE_NOTES]
#   ./scripts/distribute.sh --notes "Fix braking calculation bug" --groups "testers"
#   ./scripts/distribute.sh --testers "user@example.com"
# ==============================================================================

set -e

# Default Configuration
APP_ID="1:28790911573:android:0e08bfb1f94d5b29b6dadc"
APK_PATH="build/app/outputs/flutter-apk/app-release.apk"
RELEASE_NOTES=""
TESTERS=""
TESTER_GROUPS=""
SKIP_BUILD=false

# Styling
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}${BOLD}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}${BOLD}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}${BOLD}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}${BOLD}[ERROR]${NC} $1"
}

# Parse Command Line Arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --notes|-n)
            RELEASE_NOTES="$2"
            shift 2
            ;;
        --testers|-t)
            TESTERS="$2"
            shift 2
            ;;
        --groups|-g)
            TESTER_GROUPS="$2"
            shift 2
            ;;
        --skip-build|--no-build)
            SKIP_BUILD=true
            shift 1
            ;;
        --help|-h)
            echo "Usage: ./scripts/distribute.sh [OPTIONS] [RELEASE_NOTES]"
            echo ""
            echo "Options:"
            echo "  -n, --notes <string>     Release notes text for this distribution build"
            echo "  -t, --testers <emails>   Comma-separated list of tester emails"
            echo "  -g, --groups <aliases>   Comma-separated list of tester group aliases"
            echo "  --skip-build             Skip 'flutter build apk --release' step"
            echo "  -h, --help               Display this help message"
            echo ""
            exit 0
            ;;
        *)
            if [ -z "$RELEASE_NOTES" ]; then
                RELEASE_NOTES="$1"
            fi
            shift 1
            ;;
    esac
done

# Ensure Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    log_error "Firebase CLI (firebase-tools) is not installed."
    log_info "Install it using: npm install -g firebase-tools"
    exit 1
fi

# Fallback release notes to latest git commit message if none provided
if [ -z "$RELEASE_NOTES" ]; then
    if command -v git &> /dev/null && git rev-parse --is-inside-work-tree &> /dev/null; then
        RELEASE_NOTES=$(git log -1 --pretty=%B | tr '\n' ' ')
    fi
    if [ -z "$RELEASE_NOTES" ]; then
        RELEASE_NOTES="Automated test release - $(date '+%Y-%m-%d %H:%M:%S')"
    fi
fi

log_info "Starting distribution workflow for Firebase App ID: ${APP_ID}"
log_info "Release Notes: \"${RELEASE_NOTES}\""

# Build Release APK
if [ "$SKIP_BUILD" = false ]; then
    log_info "Building Flutter Android Release APK..."
    flutter build apk --release
    log_success "Build completed successfully."
else
    log_warn "Skipping build step (--skip-build specified)."
fi

# Verify APK existence
if [ ! -f "$APK_PATH" ]; then
    log_error "APK file not found at: ${APK_PATH}"
    log_info "Run without --skip-build to generate the APK."
    exit 1
fi

# Construct Firebase Distribution command
DISTRIBUTE_CMD=("firebase" "appdistribution:distribute" "$APK_PATH" "--app" "$APP_ID" "--release-notes" "$RELEASE_NOTES")

if [ -n "$TESTERS" ]; then
    DISTRIBUTE_CMD+=("--testers" "$TESTERS")
    log_info "Target Testers: ${TESTERS}"
fi

if [ -n "$TESTER_GROUPS" ]; then
    DISTRIBUTE_CMD+=("--groups" "$TESTER_GROUPS")
    log_info "Target Groups: ${TESTER_GROUPS}"
fi

# Upload build
log_info "Uploading release binary to Firebase App Distribution..."
"${DISTRIBUTE_CMD[@]}"

log_success "App distribution upload completed! Testers should receive a notification shortly."

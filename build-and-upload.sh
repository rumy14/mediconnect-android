#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# build-and-upload.sh — Build MediConnect APK and upload to web server
#
# Usage:
#   ./build-and-upload.sh              # bump patch version, build, upload
#   ./build-and-upload.sh --minor      # bump minor version, build, upload
#   ./build-and-upload.sh --major      # bump major version, build, upload
#   ./build-and-upload.sh --version=1.0.5   # set explicit version, build, upload
#   ./build-and-upload.sh --no-bump           # rebuild current version
#
# Output:
#   - app/build/outputs/apk/release/app-release.apk
#   → /var/www/ai-nma-it/mediconnect-v{version}.apk
#   → /var/www/ai-nma-it/mediconnect.apk          (always-latest symlink/copy)
#   - TOOLS.md is NOT auto-updated (manual after upload)
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$SCRIPT_DIR/app/build.gradle.kts"
WEB_DIR="${WEB_DIR:-/var/www/ai-nma-it}"
VERSION_REGEX='versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"'
CODE_REGEX='versionCode\s*=\s*([0-9]+)'

# ── Parse args ──
BUMP_MODE="patch"
SKIP_BUMP=false
if [[ $# -gt 0 ]]; then
  case "$1" in
    --no-bump) SKIP_BUMP=true ;;
    --minor)   BUMP_MODE="minor" ;;
    --major)   BUMP_MODE="major" ;;
    --version=*) EXPLICIT_VER="${1#*=}" ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
fi

# ── Read current version ──
CURRENT_VER=$(grep -oP 'versionName\s*=\s*"\K[0-9]+\.[0-9]+\.[0-9]+' "$GRADLE_FILE")
CURRENT_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE")

if [[ -z "$CURRENT_VER" || -z "$CURRENT_CODE" ]]; then
  echo "❌ Could not parse version from $GRADLE_FILE"
  exit 1
fi

echo "📦 Current version: $CURRENT_VER (code $CURRENT_CODE)"

# ── Determine new version ──
if [[ "${EXPLICIT_VER:-}" ]]; then
  NEW_VER="$EXPLICIT_VER"
elif [[ "$SKIP_BUMP" == true ]]; then
  NEW_VER="$CURRENT_VER"
else
  IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VER"
  case "$BUMP_MODE" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
  esac
  NEW_VER="$MAJOR.$MINOR.$PATCH"
fi

NEW_CODE=$((CURRENT_CODE + 1))

echo "📦 New version:      $NEW_VER (code $NEW_CODE)"
echo "🔨 Bump mode:        ${BUMP_MODE:-none}"
echo ""

# ── Update build.gradle.kts ──
echo "🖊️  Updating build.gradle.kts..."
sed -i "s/versionName = \"$CURRENT_VER\"/versionName = \"$NEW_VER\"/" "$GRADLE_FILE"
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
echo "   ✅ versionName → $NEW_VER, versionCode → $NEW_CODE"

# ── Build release APK ──
echo ""
echo "🔧 Building release APK..."
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

cd "$SCRIPT_DIR"
if ./gradlew assembleRelease 2>&1 | tail -5; then
  echo "   ✅ Build successful"
else
  echo "❌ Build failed"
  exit 1
fi

# ── Upload ──
APK_SRC="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
APK_VER="$WEB_DIR/mediconnect-v$NEW_VER.apk"
APK_LATEST="$WEB_DIR/mediconnect.apk"

echo ""
echo "📤 Uploading to web server..."
cp "$APK_SRC" "$APK_VER"
cp "$APK_SRC" "$APK_LATEST"
echo "   ✅ $APK_VER"
echo "   ✅ $APK_LATEST (latest)"

# ── Update version.json ──
VERSION_JSON="$WEB_DIR/mediconnect-version.json"
cat > "$VERSION_JSON" <<EOF
{
  "latestVersion": "$NEW_VER",
  "latestVersionCode": $NEW_CODE,
  "downloadUrl": "https://ai.nma-it.com/mediconnect.apk",
  "releaseNotes": "",
  "minimumVersion": "1.0.3"
}
EOF
echo "   ✅ $VERSION_JSON updated"

# ── Summary ──
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ v$NEW_VER built & uploaded"
echo "  📎 https://ai.nma-it.com/mediconnect.apk"
echo "  📎 https://ai.nma-it.com/mediconnect-v$NEW_VER.apk"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$APP_DIR/version.properties"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${JAVA_HOME:?Set JAVA_HOME}"
if [[ "${SIGN_APKS:-1}" == 1 ]]; then
: "${KEMI_WALLPAPER_KEYSTORE:?Set approved platform keystore}"
: "${KEMI_WALLPAPER_STORE_PASSWORD:?Set keystore password}"
: "${KEMI_WALLPAPER_KEY_PASSWORD:?Set key password}"
: "${KEMI_WALLPAPER_KEY_ALIAS:?Set key alias}"
fi
export PATH="$JAVA_HOME/bin:$PATH"
CACHE_ROOT="${SCREENSAVER_BUILD_ROOT:-/Volumes/ORICO/kemi-build-cache/kwallpagers-douyin-screensaver-20260913}"
[[ -d /Volumes/ORICO && -w /Volumes/ORICO ]] || { echo 'ORICO unavailable; no system-disk fallback.' >&2; exit 1; }
mkdir -p "$CACHE_ROOT/tmp" "$APP_DIR/release"
export TMPDIR="$CACHE_ROOT/tmp/"
TOOLS="$ANDROID_SDK_ROOT/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-${PLATFORM_VERSION:-31}/android.jar"
STAGE="$(mktemp -d "$CACHE_ROOT/build.XXXXXX")"
python3 "$APP_DIR/tools/verify.py" "${ALLOW_INCOMPLETE:-0}"
SUFFIX=release
VERSION="$SCREENSAVER_VERSION_NAME"
if [[ "${ALLOW_INCOMPLETE:-0}" == 1 ]]; then SUFFIX=draft; VERSION="$VERSION-draft"; fi
if [[ "${SIGN_APKS:-1}" != 1 ]]; then SUFFIX="$SUFFIX-unsigned"; fi
mkdir -p "$STAGE/ui/assets" "$STAGE/service/assets"
for item in 1:silver 2:douyin 3:spring; do
  id="${item%%:*}"; stem="${item#*:}"
  if [[ -f "$APP_DIR/assets/${stem}_video.mp4" && -f "$APP_DIR/assets/preview_${stem}.jpg" && -f "$APP_DIR/assets/${stem}_d2.png" && -f "$APP_DIR/assets/${stem}_d0.png" ]]; then
    cp "$APP_DIR/assets/${stem}_video.mp4" "$APP_DIR/assets/${stem}_d2.png" "$APP_DIR/assets/${stem}_d0.png" "$STAGE/service/assets/"
    cp "$APP_DIR/assets/preview_${stem}.jpg" "$STAGE/ui/assets/"
    printf '1' > "$STAGE/service/assets/ready_$id"
    cp "$STAGE/service/assets/ready_$id" "$STAGE/ui/assets/"
  fi
done
"$TOOLS/aapt2" compile --dir "$APP_DIR/res" -o "$STAGE/resources.zip"
SRC="$APP_DIR/src/com/kemi/dualscreensaver"
for kind in ui service; do
  mkdir -p "$STAGE/$kind/classes" "$STAGE/$kind/dex"
  if [[ "$kind" == ui ]]; then
    SOURCES=("$SRC/SettingsActivity.java" "$SRC/ScreensaverCatalog.java")
    MANIFEST="$APP_DIR/AndroidManifest.xml"; NAME=KEMI双屏屏保
  else
    SOURCES=("$SRC/ScreensaverPanelActivity.java" "$SRC/PlaybackSequence.java" "$SRC/ScreensaverDreamService.java" "$SRC/ScreensaverRenderer.java" "$SRC/ScreensaverCatalog.java" "$SRC/WallpaperLayout.java" "$SRC/VideoFrameLayout.java")
    MANIFEST="$APP_DIR/AndroidManifest-service.xml"; NAME=KEMI双屏屏保服务
  fi
  javac -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$STAGE/$kind/classes" "${SOURCES[@]}"
  CLASSES=(); while IFS= read -r file; do CLASSES+=("$file"); done < <(find "$STAGE/$kind/classes" -name '*.class')
  "$TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$STAGE/$kind/dex" "${CLASSES[@]}"
  "$TOOLS/aapt2" link -o "$STAGE/$kind/unsigned.apk" -I "$ANDROID_JAR" --manifest "$MANIFEST" --min-sdk-version 26 --target-sdk-version 31 --version-code "$SCREENSAVER_VERSION_CODE" --version-name "$VERSION" -0 mp4 -A "$STAGE/$kind/assets" "$STAGE/resources.zip"
  (cd "$STAGE/$kind/dex" && zip -q -j "$STAGE/$kind/unsigned.apk" classes.dex)
  "$TOOLS/zipalign" -f 4 "$STAGE/$kind/unsigned.apk" "$STAGE/$kind/aligned.apk"
  APK="$APP_DIR/release/$NAME-v$SCREENSAVER_VERSION_NAME-$SUFFIX.apk"
  if [[ "${SIGN_APKS:-1}" == 1 ]]; then
  "$TOOLS/apksigner" sign --ks "$KEMI_WALLPAPER_KEYSTORE" --ks-key-alias "$KEMI_WALLPAPER_KEY_ALIAS" --ks-pass env:KEMI_WALLPAPER_STORE_PASSWORD --key-pass env:KEMI_WALLPAPER_KEY_PASSWORD --out "$APK" "$STAGE/$kind/aligned.apk"
  "$TOOLS/apksigner" verify --verbose --print-certs "$APK"
  else
    cp "$STAGE/$kind/aligned.apk" "$APK"
  fi
done
python3 "$APP_DIR/tools/verify-apks.py" "$SUFFIX"

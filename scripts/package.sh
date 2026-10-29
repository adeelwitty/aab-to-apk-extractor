#!/bin/bash
# Builds the three release packages into dist/:
#   AABtoAPK-<version>-macOS-AppleSilicon.zip
#   AABtoAPK-<version>-macOS-Intel.zip
#   AABtoAPK-<version>-Windows-x64.zip
# Needs a Mac with a JDK 17+ (javac), clang, codesign, ditto, curl, zip. Downloads (and caches in .cache/):
# bundletool, FlatLaf and the Temurin 17 JREs. Usage: scripts/package.sh [version]
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="${1:-1.0.0}"
BUNDLETOOL_VERSION=1.18.3
FLATLAF_VERSION=3.5.4
CACHE=.cache; DIST=dist; BUILD=build
APPNAME="AAB to APK Extractor"
mkdir -p "$CACHE" "$DIST"

fetch() { [ -s "$CACHE/$2" ] || { echo "Downloading $2"; curl -fsSL -o "$CACHE/$2" "$1"; }; }
fetch "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar" bundletool.jar
fetch "https://repo1.maven.org/maven2/com/formdev/flatlaf/$FLATLAF_VERSION/flatlaf-$FLATLAF_VERSION.jar" flatlaf.jar
fetch "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jre/hotspot/normal/eclipse" jre-mac-arm64.tar.gz
fetch "https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jre/hotspot/normal/eclipse" jre-mac-x64.tar.gz
fetch "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse" jre-win-x64.zip

# ---- GUI jar ----
rm -rf "$BUILD" && mkdir -p "$BUILD/classes"
javac --release 17 -cp "$CACHE/flatlaf.jar" -d "$BUILD/classes" src/*.java
cp resources/*.png "$BUILD/classes/"
printf 'Main-Class: ApkExtractorApp\nClass-Path: flatlaf.jar\n' > "$BUILD/manifest.mf"
jar cfm "$BUILD/ApkExtractor.jar" "$BUILD/manifest.mf" -C "$BUILD/classes" .

# ---- macOS apps (one per CPU) ----
mac_app() { # <label> <clang arch> <jre archive> <jre dir name> <min macOS>
  local label=$1 arch=$2 jre=$3 jredir=$4 minos=$5
  local stage="$BUILD/mac-$label" app
  app="$stage/$APPNAME.app"
  rm -rf "$stage"; mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources/$jredir"
  clang -arch "$arch" -O2 -mmacosx-version-min="$minos" -DJRE_DIR="\"$jredir\"" -o "$app/Contents/MacOS/AABtoAPK" src-mac/launcher.c
  sed "s/@VERSION@/$VERSION/g; s/@MINOS@/$minos/g" src-mac/Info.plist > "$app/Contents/Info.plist"
  cp assets/AppIcon.icns "$app/Contents/Resources/"
  cp "$BUILD/ApkExtractor.jar" "$CACHE/flatlaf.jar" "$CACHE/bundletool.jar" "$app/Contents/Resources/"
  tar -xzf "$CACHE/$jre" -C "$app/Contents/Resources/$jredir" --strip-components=1
  codesign --force --deep -s - --identifier co.gamedistrict.aabtoapk "$app"
  codesign --verify --deep --strict "$app"
  local out="$DIST/AABtoAPK-$VERSION-macOS-$label.zip"
  rm -f "$out"; ditto -c -k --keepParent "$app" "$out"
  echo "Built $out"
}
mac_app AppleSilicon arm64  jre-mac-arm64.tar.gz mac-arm64 11.0
mac_app Intel        x86_64 jre-mac-x64.tar.gz   mac-x64    10.15

# ---- Windows package ----
WIN="$BUILD/win/AABtoAPK-Windows"
rm -rf "$BUILD/win"; mkdir -p "$WIN/lib" "$WIN/runtime"
cp "$BUILD/ApkExtractor.jar" "$CACHE/flatlaf.jar" "$CACHE/bundletool.jar" "$WIN/lib/"
unzip -q "$CACHE/jre-win-x64.zip" -d "$BUILD/win/jre"
mv "$BUILD"/win/jre/*/ "$WIN/runtime/win-x64"
cp "windows/$APPNAME.bat" assets/icon.ico "$WIN/"
cp README.md LICENSE THIRD_PARTY_NOTICES.md "$WIN/" 2>/dev/null || true
OUT="$PWD/$DIST/AABtoAPK-$VERSION-Windows-x64.zip"
rm -f "$OUT"; (cd "$BUILD/win" && zip -qr "$OUT" AABtoAPK-Windows)
echo "Built $OUT"

(cd "$DIST" && shasum -a 256 AABtoAPK-"$VERSION"-* > SHA256SUMS-"$VERSION".txt)
ls -lh "$DIST"

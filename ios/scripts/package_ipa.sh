#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-1.3.3}"
DERIVED="${DERIVED_DATA_PATH:-$ROOT/build}"
CONFIG="${CONFIGURATION:-Release}"
APP_SRC="$(find "$DERIVED/Build/Products/${CONFIG}-iphoneos" -name 'NeoSerf.app' -type d | head -n 1)"

if [ -z "${APP_SRC}" ]; then
  echo "NeoSerf.app not found under $DERIVED/Build/Products/${CONFIG}-iphoneos" >&2
  find "$DERIVED/Build/Products" -maxdepth 3 -type d || true
  exit 1
fi

STAGE="$(mktemp -d)"
cleanup() { rm -rf "$STAGE"; }
trap cleanup EXIT

mkdir -p "$STAGE/Payload"
cp -R "$APP_SRC" "$STAGE/Payload/NeoSerf.app"

# Unsigned sideload IPA: Payload/NeoSerf.app zipped as .ipa
mkdir -p "$ROOT/dist"
IPA="$ROOT/dist/NeoSerf-${VERSION}.ipa"
rm -f "$IPA"
(
  cd "$STAGE"
  zip -qry "$IPA" Payload
)

APP="$STAGE/Payload/NeoSerf.app"
PLIST="$APP/Info.plist"
BIN="$APP/NeoSerf"

echo "IPA: $IPA ($(du -h "$IPA" | cut -f1))"
echo "--- Info.plist ---"
/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$PLIST"
/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PLIST"
/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$PLIST"
/usr/libexec/PlistBuddy -c 'Print :CFBundleName' "$PLIST"
echo "--- Binary ---"
file "$BIN"
lipo -archs "$BIN"

ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$PLIST")"
VER="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PLIST")"
ARCHS="$(lipo -archs "$BIN")"

if [ "$ID" != "com.dimsimd.neoserf" ]; then
  echo "Unexpected CFBundleIdentifier: $ID" >&2
  exit 1
fi
if [ "$VER" != "1.3.3" ]; then
  echo "Unexpected CFBundleShortVersionString: $VER (expected 1.3.3)" >&2
  exit 1
fi
echo "$ARCHS" | grep -q 'arm64' || { echo "Binary is not arm64: $ARCHS" >&2; exit 1; }
if [ ! -f "$APP/Assets.car" ]; then
  echo "Missing Assets.car (app icon catalog was not compiled)" >&2
  ls -la "$APP" >&2
  exit 1
fi
if /usr/libexec/PlistBuddy -c 'Print :CFBundleIcons' "$PLIST" >/dev/null 2>&1; then
  echo "CFBundleIcons: present"
else
  echo "CFBundleIcons not in Info.plist; Assets.car is present so icons were compiled"
fi

echo "ipa_path=$IPA"
echo "ipa_name=$(basename "$IPA")"

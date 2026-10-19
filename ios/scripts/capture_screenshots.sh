#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$ROOT/ios"
OUT="${SCREENSHOT_OUTPUT:-$ROOT/docs/ios-screenshots}"
mkdir -p "$OUT"

cd "$IOS"
if [ ! -d NeoSerf.xcodeproj ]; then
  xcodegen generate --spec project.yml
fi

DESTINATION="$(xcrun simctl list devices available -j | python3 -c '
import json, sys
data = json.load(sys.stdin)
preferred, fallback = [], []
for devices in data.get("devices", {}).values():
    for device in devices:
        if not device.get("isAvailable"):
            continue
        name = device.get("name", "")
        dest = "platform=iOS Simulator,id=%s" % device["udid"]
        if name.startswith("iPhone"):
            if any(token in name for token in ("16", "15", "SE", "17")):
                preferred.append((name, dest))
            else:
                fallback.append((name, dest))
order = preferred + fallback
if not order:
    sys.exit("No available iPhone simulator")
sys.stderr.write("Simulator: %s\n" % order[0][0])
print(order[0][1])
')"

echo "Using destination: $DESTINATION"
echo "Writing screenshots to $OUT"

RESULT_BUNDLE="$IOS/build-screenshots/NeoSerfUITests.xcresult"
rm -rf "$RESULT_BUNDLE"

set +e
xcodebuild test \
  -project NeoSerf.xcodeproj \
  -scheme NeoSerf \
  -destination "$DESTINATION" \
  -only-testing:NeoSerfUITests/ScreenshotTests \
  -derivedDataPath "$IOS/build-screenshots" \
  -resultBundlePath "$RESULT_BUNDLE" \
  CODE_SIGNING_ALLOWED=YES \
  CODE_SIGN_IDENTITY=- \
  CODE_SIGNING_REQUIRED=NO
TEST_STATUS=$?
set -e

if [ ! -d "$RESULT_BUNDLE" ]; then
  RESULT_BUNDLE="$(find "$IOS/build-screenshots/Logs/Test" -name '*.xcresult' | tail -n 1 || true)"
fi

if [ -n "${RESULT_BUNDLE:-}" ] && [ -d "$RESULT_BUNDLE" ]; then
  echo "Exporting attachments from $RESULT_BUNDLE"
  python3 "$ROOT/ios/scripts/export_xcresult_screenshots.py" "$RESULT_BUNDLE" "$OUT" || true
  find "$IOS/build-screenshots" -name '*.png' ! -name 'AppIcon*' -exec cp -n {} "$OUT"/ \; 2>/dev/null || true
fi

# Keep README if the directory already had one.
rm -f "$OUT"/AppIcon*.png
EXPECTED=(home loaded menu settings tabs shield first-run)
COUNT=0
for screen in "${EXPECTED[@]}"; do
  for appearance in light dark; do
    if [ -f "$OUT/${screen}-${appearance}.png" ]; then
      COUNT=$((COUNT + 1))
    fi
  done
done
echo "Captured ${COUNT} named screenshots in $OUT"
ls -la "$OUT"
if [ "$TEST_STATUS" -ne 0 ]; then
  echo "xcodebuild test failed with status $TEST_STATUS" >&2
  exit "$TEST_STATUS"
fi
if [ "$COUNT" -lt 14 ]; then
  echo "Expected 14 screenshots (home/loaded/menu/settings/tabs/shield/first-run × light/dark)" >&2
  exit 1
fi

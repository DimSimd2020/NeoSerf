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

export SCREENSHOT_OUTPUT="$OUT"

xcodebuild test \
  -project NeoSerf.xcodeproj \
  -scheme NeoSerf \
  -destination "$DESTINATION" \
  -only-testing:NeoSerfUITests/ScreenshotTests \
  -derivedDataPath "$IOS/build-screenshots" \
  CODE_SIGNING_ALLOWED=YES \
  CODE_SIGN_IDENTITY=- \
  CODE_SIGNING_REQUIRED=NO

COUNT="$(find "$OUT" -name '*.png' | wc -l | tr -d ' ')"
echo "Captured ${COUNT} PNG files in $OUT"
ls -la "$OUT"
if [ "$COUNT" -lt 8 ]; then
  echo "Expected at least 8 screenshots (home/loaded/menu/settings/tabs/shield/first-run × light/dark)" >&2
  exit 1
fi

# NeoSerf iOS

Native SwiftUI + WKWebView port of NeoSerf. Local-only (no Firebase). Sideload the unsigned IPA with Scarlet.

- Bundle ID: `com.dimsimd.neoserf`
- Deployment target: iOS 15+
- Version: `1.3.3` (`CFBundleVersion` 12)
- UI: SwiftUI port of the current Android Compose chrome (quiet home, custom SVG/PDF icons, fin logo, top-right bookmarks/menu, settings bottom sheet, weather location prompt, first-run). Local-only, no Firebase.

## Project

The Xcode project is generated from [`project.yml`](project.yml) with [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
brew install xcodegen
cd ios
xcodegen generate
open NeoSerf.xcodeproj
```

## Unsigned IPA (same flags as CI)

```bash
cd ios
xcodegen generate
xcodebuild -project NeoSerf.xcodeproj -scheme NeoSerf \
  -configuration Release -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build
../ios/scripts/package_ipa.sh 1.3.3
```

Scarlet re-signs the IPA with its own certificate, so the CI artifact is intentionally unsigned.

## Simulator screenshots

CI also builds for the iOS Simulator and runs `NeoSerfUITests` to capture light and dark shots of home, a loaded page, menu, settings, tabs, shield and first-run. They are uploaded as the `ios-screenshots` artifact and committed under [`docs/ios-screenshots/`](../docs/ios-screenshots/).

```bash
cd ios
xcodegen generate
SCREENSHOT_OUTPUT=../docs/ios-screenshots ../ios/scripts/capture_screenshots.sh
```

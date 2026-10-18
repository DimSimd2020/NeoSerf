# NeoSerf

A fast, private Android browser with a graphite / neon-blue theme, built-in ad and tracker blocking, reader mode, desktop mode, incognito tabs, and Google-account cloud sync for bookmarks, history, and an AES-GCM password vault.

**Android:** Kotlin, Jetpack Compose, Gradle. Application ID `com.aistudio.neoserf.kxpqzm`, `versionName` `1.2.0` (`versionCode` 7). Without Firebase config the app opens a local guest browser instead of crashing. The existing guest repository stores bookmarks, history and vault entries in memory; they do not survive process termination.

**Desktop:** native C++17, Qt Widgets and Qt WebEngine. See [desktop build instructions](desktop/README.md). Windows CI builds a portable runtime package; no Tauri, Rust, Electron or Node.js runtime is used by the app.

## Interface and weather

The start page uses a uniform neutral gray (`#191919`), restrained blue accents (`#459fff`), a centered vector logo, search field and compact weather. Search providers, protection and customization are available through the compact translucent overflow menu. Shortcuts can be enabled in Settings; they are hidden by default.

Settings → Оформление offers **Как на устройстве**, **Светлая** and **Тёмная** on Android and desktop. The default follows the device theme and responds to changes while the app is open; a manual choice is saved across launches. Light mode uses neutral gray (`#f3f3f3`) with a darker blue accent (`#166ad8`) for contrast. The supplied blue/cyan application logo stays the same in both themes.

All application action and weather icons come from the custom SVG set in `desktop/assets/icons/`. Qt renders those sources with the current palette; Android uses generated native Compose vectors. After editing an SVG, run `python tools/generate_icons.py` to update the Android vectors and Qt resource list. No SVG runtime library is added to Android.

Weather uses [Open-Meteo](https://open-meteo.com/) and detects the network region through [ipwho.is](https://ipwhois.io/). On Android, Settings → Уточнить местоположение requests approximate location permission; once allowed, fresh device coordinates take precedence. Without permission or a usable device location, network detection remains automatic. A VPN can change the network region. No fixed city or invented temperature is substituted for errors. Current conditions refresh every 30 minutes while the widget is enabled. Android requires `INTERNET` and optional `ACCESS_COARSE_LOCATION`; no location API key or background-location permission is needed.

## Build locally

Requirements: JDK 21 and the Android SDK (`compileSdk` 36.1, `minSdk` 24).

```bash
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.

Release signing uses a keystore at `KEYSTORE_PATH` (default `my-upload-key.jks`) with alias `upload`, matching the AI Studio layout. Do not commit production keystores.

## CI

GitHub Actions (`.github/workflows/android.yml`) builds an installable APK on:

- push to `main`
- tags matching `v*`
- pull requests targeting `main`
- manual **Run workflow** (`workflow_dispatch`)

The APK is uploaded as the `NeoSerf-apk` workflow artifact. Pushing a `v*` tag also creates a GitHub Release and attaches the APK.

## Release signing secrets

If no release keystore secrets are configured, CI signs with the committed **sideload-only** keystore `ci/sideload-debug.keystore` (alias `androiddebugkey`, password `android`). That signature is stable across CI runs so consecutive sideload APKs can update each other (`adb install -r`). It is **not** for Play Console or production.

To sign with your own upload keystore, add these repository secrets (`Settings → Secrets and variables → Actions`):

| Secret | Description |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |
| `GOOGLE_SERVICES_JSON` | Optional. Raw `google-services.json` (or its base64). When set, CI writes `app/google-services.json` before the build so Firebase Auth/Firestore are configured. |

Encode the keystore (do not commit the file or the encoded value):

```bash
base64 -w0 upload-key.jks
```

When all four secrets are present, the workflow decodes the keystore and Gradle uses them for `:app:assembleRelease`. Local AI Studio variables `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` continue to work.

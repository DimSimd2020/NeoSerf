# NeoSerf

A fast, private Android browser with a graphite / neon-blue theme, built-in ad and tracker blocking, reader mode, desktop mode, incognito tabs, and Google-account cloud sync for bookmarks, history, and an AES-GCM password vault.

**Stack:** native Android (Kotlin, Jetpack Compose, Gradle). Application ID `com.aistudio.neoserf.kxpqzm`, `versionName` `1.0.4` (`versionCode` 5). Without Firebase config the app opens a local guest browser instead of crashing.

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

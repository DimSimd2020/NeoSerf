# iOS UI screenshots

These images are captured on the macOS GitHub Actions runner by `NeoSerfUITests/ScreenshotTests`: the app boots in the iOS Simulator in Russian, with a fixture weather card and no location prompt, then `XCUIScreenshot` saves each key screen in light and dark.

| File | Screen |
| --- | --- |
| `home-light.png` / `home-dark.png` | Quiet start page (fin logo, search, weather, top-right bookmarks/menu) |
| `loaded-light.png` / `loaded-dark.png` | Omnibox top bar on a loaded page |
| `menu-light.png` / `menu-dark.png` | Overflow menu |
| `settings-light.png` / `settings-dark.png` | Settings bottom sheet |
| `tabs-light.png` / `tabs-dark.png` | Tabs sheet with incognito |
| `shield-light.png` / `shield-dark.png` | Privacy shield sheet |
| `first-run-light.png` / `first-run-dark.png` | First-run theme step |

Compare them against the Android Compose sources in `app/src/main/java/com/example/ui/` (theme, bars, start page, settings, sheets). Android CI does not currently emit Paparazzi/Robolectric bitmaps; the Compose files remain the layout spec.

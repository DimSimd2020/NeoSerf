# NeoSerf Desktop

Native C++17 and Qt Widgets browser, using Qt WebEngine for web pages. There is no Node.js, Rust, Tauri, Electron, or JavaScript frontend in the application. The home page, settings, menus, tabs and navigation are native widgets. Qt WebEngine includes Chromium and its own subprocesses; the complete package includes that browser engine.

## Windows build

Install MSVC x64 C++ desktop build tools, a Windows SDK, CMake, Ninja and Qt 6.8 or newer with WebEngine, WebChannel, Positioning and SVG. This version was built and tested with Qt **6.10.3**. Keep Qt WebEngine up to date when distributing a browser.

From PowerShell:

```powershell
.\desktop\build.ps1 -QtRoot 'C:\Qt\6.10.3\msvc2022_64'
```

The script initializes MSVC, builds the browser, runs QtTest and packages the Qt runtime. Launch `desktop/dist/NeoSerf/bin/NeoSerf.exe`. Copy the **whole NeoSerf folder**, including resources, plugins, translations and bin. The EXE alone is not a portable browser.

### Installer

After `build.ps1`, build the Windows installer with [Inno Setup 6](https://jrsoftware.org/isdl.php):

```powershell
.\desktop\installer\build.ps1
```

It writes `desktop/dist/installer/NeoSerf-Setup-<version>-x64.exe`. The installer is per-user by default (no administrator prompt), adds a Start menu shortcut, an optional desktop shortcut and an uninstaller. Desktop CI builds it on every run; for a `v*` tag it is attached to the GitHub Release next to the Android APK.

Generic CMake build (use an MSVC developer shell on Windows):

```sh
cmake -S desktop -B desktop/build -DCMAKE_PREFIX_PATH=/path/to/Qt -DCMAKE_BUILD_TYPE=Release
cmake --build desktop/build --config Release
ctest --test-dir desktop/build -C Release --output-on-failure
cmake --install desktop/build --config Release --prefix /absolute/path/to/package
```

## Behavior

- Compact tabs above the address bar, with the active tab joining the navigation surface. The new-tab button stays next to the tab strip; excess tabs scroll. Dragging tabs also reorders the restored session.

- First launch shows a two-step welcome screen with an animated slide between steps. Step 1: theme (previewed live), interface language (Russian or English, applied from the next launch) and import of bookmarks and up to 500 history pages from Chrome, Opera, Opera GX or Yandex Browser. History is read from a private copy, so a running browser does not block the import. Passwords, logins and extensions are not imported. Skipping is fine; import is also under Settings → Перенос данных. Step 2: default search engine and default mail.
- "Почта" link in the top-right corner of the home page (under the navigation bar) opens the default mail service (Gmail unless changed in Settings → Поиск и почта; Yandex Mail, Outlook, Mail.ru or any HTTPS address).
- An arrow button inside the address bar and the home search submits the text (shown while there is text). There is no clear button.
- Settings use a Chrome-style sidebar: appearance and language, search and mail, startup (restore tabs or new tab), privacy (keep/block third-party cookies, clear history on exit, clear browsing data), sites (JavaScript, images, pop-ups, autoplay, smooth scrolling, dark mode for pages, location/camera/microphone rules), fonts and zoom, downloads (ask or save to a folder), data import, and reset. Chrome has hundreds of switches; NeoSerf offers the ones Qt WebEngine can really apply and does not show controls that do nothing.
- Settings → Оформление: follow the system (default), light or dark. The system mode updates an open window when the device theme changes; manual choices persist. SVG action icons, menus, dialogs, form controls and the title bar use the selected palette.

- Real web browsing, back/forward, reload, home, multiple tabs and new-tab links.
- Search or URL in the center of the home page; the search provider lives in Settings.
- Gray `#191919` background, blue `#459fff` accents and the same vector monogram as Android.
- Compact translucent menu with a short fade animation.
- Regular tabs, profile cookies and site data persist locally. The last regular session is restored.
- Private tabs use an off-the-record Qt profile and are excluded from saved sessions and history. Explicitly saving a bookmark or downloading a file still writes that item to disk.
- Local bookmarks and history (up to 500 visits), file downloads with a save dialog, find in page, zoom, and website permission prompts.
- Weather detects the network region over HTTPS through ipwho.is and loads real current conditions from Open-Meteo. It refreshes every 30 minutes. A VPN can affect the region. Network or incomplete-data errors display a retry action. Weather can be disabled in Settings.

Shortcuts: `Ctrl+L` address, `Ctrl+T` new tab, `Ctrl+W`/`Ctrl+F4` close tab, `Ctrl+Shift+N` private tab, `Ctrl+Tab` next tab, `Ctrl+R` reload, `Alt+Left`/`Alt+Right` navigation, `Alt+Home` start, `Ctrl+D` bookmark, `Ctrl+F` find, `Ctrl++`/`Ctrl+-` zoom, `Ctrl+0` reset zoom.

Settings, bookmarks and history use the platform's QSettings storage. WebEngine profile data is stored in the platform's NeoSerf AppData directory. This desktop version does not implement the Android Firebase cloud sync, password vault or ad blocker. It does not display claims that these features are enabled.

## Dependencies and notices

Qt and Qt WebEngine are dynamically linked. Qt is available under its applicable commercial/open-source licenses; Chromium and included third-party libraries have their own notices. Consult [Qt licensing](https://doc.qt.io/qt-6/licensing.html) and [Qt WebEngine licensing](https://doc.qt.io/qt-6/qtwebengine-licensing.html) before redistributing a package. Weather sources: [Open-Meteo](https://open-meteo.com/) and [ipwho.is](https://ipwhois.io/).

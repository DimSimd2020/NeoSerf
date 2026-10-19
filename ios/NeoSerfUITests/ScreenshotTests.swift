import XCTest

final class ScreenshotTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    func testCaptureKeyScreensLightAndDark() throws {
        let output = screenshotDirectory()
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        for appearance in ["light", "dark"] {
            capture(screen: "home", appearance: appearance, output: output, extra: [])
            capture(screen: "loaded", appearance: appearance, output: output, extra: ["-uitest-load-fixture"])
            captureMenu(appearance: appearance, output: output)
            capture(screen: "settings", appearance: appearance, output: output, extra: ["-uitest-sheet", "settings"])
            capture(screen: "tabs", appearance: appearance, output: output, extra: ["-uitest-sheet", "tabs"])
            capture(screen: "shield", appearance: appearance, output: output, extra: ["-uitest-sheet", "shield"])
            captureFirstRun(appearance: appearance, output: output)
        }
    }

    private func capture(screen: String, appearance: String, output: URL, extra: [String]) {
        let app = launch(appearance: appearance, extra: extra + ["-uitest-skip-onboarding"])
        if screen == "home" {
            _ = wait(app, "start_page") || wait(app, "nav_menu_button")
        } else if screen == "loaded" {
            _ = wait(app, "page_loaded") || wait(app, "omnibox_reload_button") || wait(app, "omnibox_input")
        } else if screen == "settings" {
            _ = wait(app, "settings_sheet") || wait(app, "settings_theme_SYSTEM")
        } else if screen == "tabs" {
            _ = wait(app, "tabs_filter_regular") || wait(app, "new_regular_tab_button")
        } else if screen == "shield" {
            _ = wait(app, "adblock_master_switch")
        }
        Thread.sleep(forTimeInterval: screen == "loaded" ? 1.6 : 0.8)
        saveScreenshot(app, name: "\(screen)-\(appearance)", output: output)
        app.terminate()
    }

    private func captureMenu(appearance: String, output: URL) {
        let app = launch(appearance: appearance, extra: ["-uitest-skip-onboarding"])
        _ = wait(app, "nav_menu_button")
        let menuButton = app.descendants(matching: .any)["nav_menu_button"]
        if menuButton.exists { menuButton.tap() }
        _ = wait(app, "browser_overflow_menu") || wait(app, "menu_settings")
        Thread.sleep(forTimeInterval: 0.5)
        saveScreenshot(app, name: "menu-\(appearance)", output: output)
        app.terminate()
    }

    private func captureFirstRun(appearance: String, output: URL) {
        let app = launch(appearance: appearance, extra: ["-uitest-first-run"])
        _ = wait(app, "welcome_screen") || wait(app, "welcome_next")
        Thread.sleep(forTimeInterval: 0.5)
        saveScreenshot(app, name: "first-run-\(appearance)", output: output)
        app.terminate()
    }

    private func launch(appearance: String, extra: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest",
            "-uitest-skip-location",
            "-uitest-fixture-weather",
            "-uitest-appearance", appearance,
            "-AppleLanguages", "(ru)",
            "-AppleLocale", "ru_RU"
        ] + extra
        app.launch()
        return app
    }

    @discardableResult
    private func wait(_ app: XCUIApplication, _ identifier: String) -> Bool {
        app.descendants(matching: .any)[identifier].waitForExistence(timeout: 8)
    }

    private func saveScreenshot(_ app: XCUIApplication, name: String, output: URL) {
        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let data = screenshot.pngRepresentation as Data? {
            try? data.write(to: output.appendingPathComponent("\(name).png"))
        }
    }

    private func screenshotDirectory() -> URL {
        if let raw = ProcessInfo.processInfo.environment["SCREENSHOT_OUTPUT"], !raw.isEmpty {
            return URL(fileURLWithPath: raw)
        }
        let workspace = ProcessInfo.processInfo.environment["GITHUB_WORKSPACE"]
            ?? ProcessInfo.processInfo.environment["SRCROOT"]
            ?? FileManager.default.temporaryDirectory.path
        return URL(fileURLWithPath: workspace).appendingPathComponent("docs/ios-screenshots")
    }
}

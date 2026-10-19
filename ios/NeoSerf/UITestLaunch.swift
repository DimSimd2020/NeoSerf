import Foundation

enum UITestLaunch {
    static var arguments: [String] { ProcessInfo.processInfo.arguments }

    static var isUITest: Bool { arguments.contains("-uitest") }
    static var skipOnboarding: Bool { arguments.contains("-uitest-skip-onboarding") }
    static var forceFirstRun: Bool { arguments.contains("-uitest-first-run") }
    static var skipLocation: Bool { arguments.contains("-uitest-skip-location") }
    static var loadFixture: Bool { arguments.contains("-uitest-load-fixture") }
    static var fixtureWeather: Bool { arguments.contains("-uitest-fixture-weather") }

    static var appearance: String? { value(after: "-uitest-appearance") }
    static var openSheet: String? { value(after: "-uitest-sheet") }

    private static func value(after flag: String) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else { return nil }
        let value = arguments[index + 1]
        return value.hasPrefix("-") ? nil : value
    }
}

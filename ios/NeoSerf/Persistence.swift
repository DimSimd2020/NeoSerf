import Foundation

enum LocalStore {
    private static let defaults = UserDefaults.standard
    private static let prefsKey = "neoserf.preferences.v1"

    private static var supportDir: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let dir = base.appendingPathComponent("NeoSerf", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    static func loadPreferences() -> BrowserPreferences {
        guard let data = defaults.data(forKey: prefsKey),
              let prefs = try? JSONDecoder().decode(BrowserPreferences.self, from: data) else {
            return BrowserPreferences()
        }
        return prefs
    }

    static func savePreferences(_ prefs: BrowserPreferences) {
        if let data = try? JSONEncoder().encode(prefs) {
            defaults.set(data, forKey: prefsKey)
        }
    }

    static func loadBookmarks() -> [BookmarkItem] { load("bookmarks.json") }
    static func saveBookmarks(_ items: [BookmarkItem]) { save(items, as: "bookmarks.json") }

    static func loadHistory() -> [HistoryEntry] { load("history.json") }
    static func saveHistory(_ items: [HistoryEntry]) { save(items, as: "history.json") }

    static func loadSpeedDial() -> [SpeedDialTile] { load("speeddial.json") }
    static func saveSpeedDial(_ items: [SpeedDialTile]) { save(items, as: "speeddial.json") }

    private static func load<T: Decodable>(_ name: String) -> [T] {
        let url = supportDir.appendingPathComponent(name)
        guard let data = try? Data(contentsOf: url),
              let items = try? JSONDecoder().decode([T].self, from: data) else {
            return []
        }
        return items
    }

    private static func save<T: Encodable>(_ items: [T], as name: String) {
        let url = supportDir.appendingPathComponent(name)
        if let data = try? JSONEncoder().encode(items) {
            try? data.write(to: url, options: .atomic)
        }
    }
}

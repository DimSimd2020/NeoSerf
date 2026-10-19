import Foundation

enum SearchEngine: String, CaseIterable, Identifiable, Codable {
    case google = "Google"
    case duckDuckGo = "DuckDuckGo"
    case yandex = "Yandex"
    case bing = "Bing"
    case brave = "Brave"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .google: return "Google"
        case .duckDuckGo: return "DuckDuckGo"
        case .yandex: return "Яндекс"
        case .bing: return "Microsoft Bing"
        case .brave: return "Brave Search"
        }
    }

    func buildSearchUrl(_ query: String) -> String {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        switch self {
        case .google: return "https://www.google.com/search?q=\(encoded)"
        case .duckDuckGo: return "https://duckduckgo.com/?q=\(encoded)"
        case .yandex: return "https://ya.ru/search/?text=\(encoded)"
        case .bing: return "https://www.bing.com/search?q=\(encoded)"
        case .brave: return "https://search.brave.com/search?q=\(encoded)"
        }
    }

    static func fromId(_ id: String) -> SearchEngine {
        SearchEngine(rawValue: id) ?? .google
    }
}

enum ReaderTheme: String, CaseIterable, Identifiable, Codable {
    case graphite
    case amoled
    case sepia

    var id: String { rawValue }

    var labelRu: String {
        switch self {
        case .graphite: return "Графит"
        case .amoled: return "Черный"
        case .sepia: return "Сепия"
        }
    }
}

struct ReaderArticle: Equatable {
    var title: String
    var domain: String
    var byline: String
    var paragraphs: [String]
    var estimatedMinutes: Int
}

struct BrowserTab: Identifiable, Equatable {
    let id: UUID
    var title: String
    var url: String
    var isIncognito: Bool
    var isDesktopMode: Bool
    var isReaderMode: Bool
    var readerArticle: ReaderArticle?
    var isLoading: Bool
    var progress: Double
    var canGoBack: Bool
    var canGoForward: Bool
    var adsBlockedInTab: Int
    var trackersBlockedInTab: Int

    var isStartPage: Bool { url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    init(
        id: UUID = UUID(),
        title: String = "Стартовая страница",
        url: String = "",
        isIncognito: Bool = false,
        isDesktopMode: Bool = false,
        isReaderMode: Bool = false,
        readerArticle: ReaderArticle? = nil,
        isLoading: Bool = false,
        progress: Double = 0,
        canGoBack: Bool = false,
        canGoForward: Bool = false,
        adsBlockedInTab: Int = 0,
        trackersBlockedInTab: Int = 0
    ) {
        self.id = id
        self.title = title
        self.url = url
        self.isIncognito = isIncognito
        self.isDesktopMode = isDesktopMode
        self.isReaderMode = isReaderMode
        self.readerArticle = readerArticle
        self.isLoading = isLoading
        self.progress = progress
        self.canGoBack = canGoBack
        self.canGoForward = canGoForward
        self.adsBlockedInTab = adsBlockedInTab
        self.trackersBlockedInTab = trackersBlockedInTab
    }
}

struct BookmarkItem: Identifiable, Codable, Equatable {
    var id: String
    var title: String
    var url: String
    var folder: String
    var createdAt: Date
}

struct HistoryEntry: Identifiable, Codable, Equatable {
    var id: String
    var title: String
    var url: String
    var searchEngine: String
    var createdAt: Date
}

struct SpeedDialTile: Identifiable, Codable, Equatable {
    var id: String
    var title: String
    var url: String
    var category: String
    var position: Int
}

struct BrowserPreferences: Equatable, Codable {
    var appTheme: AppTheme = .system
    var searchEngine: SearchEngine = .google
    var showWeatherWidget: Bool = true
    var showSpeedDial: Bool = false
    var adBlockEnabled: Bool = true
    var totalAdsBlocked: Int = 0
    var totalTrackersBlocked: Int = 0
    var whitelistedDomains: [String] = []
    var readerFontSize: Int = 18
    var readerTheme: ReaderTheme = .graphite
    var locationPromptShown: Bool = false
    var speedDialSeeded: Bool = false
}

struct WeatherForecast: Equatable {
    var cityName: String
    var country: String
    var temperatureC: Double
    var apparentTempC: Double
    var windSpeedKmh: Double
    var humidityPercent: Int
    var weatherCode: Int
    var isDay: Bool
    var locationSource: String
    var dataSource: String
    var conditionText: String
    var dailyMaxC: Double
    var dailyMinC: Double
}

enum WeatherState: Equatable {
    case loading
    case success(WeatherForecast)
    case error(String)
}

enum WebCommandKind: Equatable {
    case load(String)
    case reload
    case back
    case forward
    case extractReader
}

struct WebCommand: Equatable {
    let token: Int
    let kind: WebCommandKind
}

enum ActiveSheet: String, Identifiable {
    case tabs
    case shield
    case settings
    case bookmarks
    case history

    var id: String { rawValue }
}

enum BlockCategory {
    case allowed
    case ad
    case tracker
}

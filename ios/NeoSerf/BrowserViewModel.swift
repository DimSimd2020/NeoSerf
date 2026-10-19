import Combine
import CoreLocation
import Foundation

@MainActor
final class BrowserViewModel: ObservableObject {
    @Published var tabs: [BrowserTab]
    @Published var activeTabId: UUID
    @Published var preferences: BrowserPreferences {
        didSet { LocalStore.savePreferences(preferences) }
    }
    @Published var bookmarks: [BookmarkItem] {
        didSet { LocalStore.saveBookmarks(bookmarks) }
    }
    @Published var history: [HistoryEntry] {
        didSet { LocalStore.saveHistory(history) }
    }
    @Published var speedDial: [SpeedDialTile] {
        didSet { LocalStore.saveSpeedDial(speedDial) }
    }
    @Published var passwords: [PasswordEntry] {
        didSet { LocalStore.savePasswords(passwords) }
    }
    @Published var weather: WeatherState = .loading
    @Published var statusMessage: String?
    @Published var webCommand: WebCommand?
    @Published var menuExpanded = false
    @Published var activeSheet: ActiveSheet?

    private var commandToken = 0
    private var weatherTask: Task<Void, Never>?
    private var locationPromptClaimedThisLaunch = false

    init() {
        if UITestLaunch.isUITest {
            LocalStore.resetForUITest()
        }
        var prefs = LocalStore.loadPreferences()
        if UITestLaunch.forceFirstRun {
            prefs.onboardingCompleted = false
        } else if UITestLaunch.skipOnboarding {
            prefs.onboardingCompleted = true
        }
        if UITestLaunch.skipLocation {
            prefs.locationPromptShown = true
        }
        preferences = prefs
        LocalStore.savePreferences(prefs)
        bookmarks = LocalStore.loadBookmarks()
        history = LocalStore.loadHistory()
        speedDial = LocalStore.loadSpeedDial()
        passwords = LocalStore.loadPasswords()
        let first = BrowserTab()
        tabs = [first]
        activeTabId = first.id
        if !prefs.speedDialSeeded && speedDial.isEmpty {
            seedSpeedDial()
        }
        ContentBlockerStore.shared.prepare()
        if UITestLaunch.fixtureWeather {
            weather = .success(.screenshotFixture)
        } else {
            startWeatherLoop()
        }
        if UITestLaunch.loadFixture, let url = Bundle.main.url(forResource: "fixture_page", withExtension: "html") {
            DispatchQueue.main.async { [weak self] in
                self?.submitAddressOrQuery(url.absoluteString)
            }
        }
        if let sheet = UITestLaunch.openSheet {
            DispatchQueue.main.async { [weak self] in
                self?.activeSheet = ActiveSheet(rawValue: sheet)
            }
        }
    }

    var activeTab: BrowserTab {
        tabs.first(where: { $0.id == activeTabId }) ?? tabs[0]
    }

    var isCurrentUrlBookmarked: Bool {
        let url = activeTab.url
        return !url.isEmpty && bookmarks.contains { $0.url.compare(url, options: .caseInsensitive) == .orderedSame }
    }

    func emit(_ kind: WebCommandKind) {
        commandToken += 1
        webCommand = WebCommand(token: commandToken, kind: kind)
    }

    func showMessage(_ text: String) {
        statusMessage = text
    }

    // MARK: - Tabs

    func createNewTab(incognito: Bool, url: String = "") {
        let tab = BrowserTab(
            title: incognito ? "Инкогнито" : "Стартовая страница",
            url: url,
            isIncognito: incognito
        )
        tabs.append(tab)
        activeTabId = tab.id
        menuExpanded = false
        if !url.isEmpty {
            emit(.load(url))
        }
    }

    func selectTab(_ id: UUID) {
        if tabs.contains(where: { $0.id == id }) {
            activeTabId = id
        }
        activeSheet = nil
    }

    func closeTab(_ id: UUID) {
        if tabs.count <= 1 {
            updateTab(tabs[0].id) {
                $0.title = $0.isIncognito ? "Инкогнито" : "Стартовая страница"
                $0.url = ""
                $0.isReaderMode = false
                $0.readerArticle = nil
                $0.isLoading = false
                $0.progress = 0
            }
            return
        }
        let index = tabs.firstIndex(where: { $0.id == id }) ?? 0
        tabs.removeAll { $0.id == id }
        if activeTabId == id {
            let next = min(max(index - 1, 0), tabs.count - 1)
            activeTabId = tabs[next].id
        }
    }

    func closeAllIncognito() {
        let remaining = tabs.filter { !$0.isIncognito }
        if remaining.isEmpty {
            let fresh = BrowserTab()
            tabs = [fresh]
            activeTabId = fresh.id
        } else {
            tabs = remaining
            if remaining.allSatisfy({ $0.id != activeTabId }) {
                activeTabId = remaining.last!.id
            }
        }
        showMessage("Все вкладки Инкогнито закрыты")
        activeSheet = nil
    }

    func updateTab(_ id: UUID, transform: (inout BrowserTab) -> Void) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        var tab = tabs[index]
        transform(&tab)
        tabs[index] = tab
    }

    // MARK: - Navigation

    static func resolveInputToUrl(_ input: String, engine: SearchEngine) -> String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.lowercased().hasPrefix("https://")
            || trimmed.lowercased().hasPrefix("http://")
            || trimmed.lowercased().hasPrefix("file://") {
            return trimmed
        }
        let looksLikeDomain = !trimmed.contains(" ") && trimmed.contains(".") &&
            (trimmed.split(separator: ".").last?.count ?? 0) >= 2
        if looksLikeDomain {
            return "https://\(trimmed)"
        }
        return engine.buildSearchUrl(trimmed)
    }

    func submitAddressOrQuery(_ raw: String) {
        let input = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { return }
        let resolved = Self.resolveInputToUrl(input, engine: preferences.searchEngine)
        let id = activeTab.id
        updateTab(id) {
            $0.url = resolved
            $0.title = input
            $0.isReaderMode = false
            $0.readerArticle = nil
            $0.isLoading = true
            $0.progress = 0.15
        }
        emit(.load(resolved))
        menuExpanded = false
        activeSheet = nil
    }

    func navigateHome() {
        updateTab(activeTab.id) {
            $0.url = ""
            $0.title = $0.isIncognito ? "Инкогнито" : "Стартовая страница"
            $0.isReaderMode = false
            $0.readerArticle = nil
            $0.isLoading = false
            $0.progress = 0
            $0.canGoBack = false
            $0.canGoForward = false
        }
    }

    func goBack() {
        if activeTab.isReaderMode {
            updateTab(activeTab.id) { $0.isReaderMode = false }
            return
        }
        if !activeTab.isStartPage && !activeTab.canGoBack {
            navigateHome()
            return
        }
        emit(.back)
    }

    func goForward() { emit(.forward) }
    func reload() { emit(.reload) }

    func onPageStarted(tabId: UUID, url: String) {
        updateTab(tabId) {
            $0.url = url
            $0.isLoading = true
            $0.progress = max($0.progress, 0.2)
        }
    }

    func onProgress(tabId: UUID, progress: Double) {
        updateTab(tabId) {
            $0.progress = progress
            $0.isLoading = progress < 1
        }
    }

    func onPageFinished(tabId: UUID, url: String, title: String?, canGoBack: Bool, canGoForward: Bool) {
        let domain = AdBlocker.extractDomain(url)
        let cleanTitle = (title?.isEmpty == false ? title! : (domain.isEmpty ? url : domain))
        var record = false
        updateTab(tabId) {
            record = !$0.isIncognito && url.hasPrefix("http")
            $0.url = url
            $0.title = cleanTitle
            $0.isLoading = false
            $0.progress = 1
            $0.canGoBack = canGoBack
            $0.canGoForward = canGoForward
        }
        if record {
            let entry = HistoryEntry(
                id: UUID().uuidString,
                title: cleanTitle,
                url: url,
                searchEngine: preferences.searchEngine.displayName,
                createdAt: Date()
            )
            history.insert(entry, at: 0)
            if history.count > 500 { history = Array(history.prefix(500)) }
        }
    }

    func onBlocked(tabId: UUID, category: BlockCategory) {
        guard category != .allowed else { return }
        let ads = category == .ad ? 1 : 0
        let trackers = category == .tracker ? 1 : 0
        updateTab(tabId) {
            $0.adsBlockedInTab += ads
            $0.trackersBlockedInTab += trackers
        }
        preferences.totalAdsBlocked += ads
        preferences.totalTrackersBlocked += trackers
    }

    func applyCosmeticCount(tabId: UUID, count: Int) {
        guard count > 0 else { return }
        updateTab(tabId) { $0.adsBlockedInTab += count }
        preferences.totalAdsBlocked += count
    }

    // MARK: - Modes

    func toggleDesktopMode() {
        let next = !activeTab.isDesktopMode
        updateTab(activeTab.id) { $0.isDesktopMode = next }
        showMessage(next ? "Включен режим ПК" : "Включен мобильный режим")
        if !activeTab.isStartPage { emit(.reload) }
    }

    func toggleReaderMode() {
        if activeTab.isStartPage {
            showMessage("Откройте веб-страницу или статью для включения режима чтения")
            return
        }
        if activeTab.isReaderMode {
            updateTab(activeTab.id) { $0.isReaderMode = false }
        } else {
            emit(.extractReader)
        }
    }

    func onReaderArticle(_ article: ReaderArticle, tabId: UUID) {
        updateTab(tabId) {
            $0.isReaderMode = true
            $0.readerArticle = article
        }
    }

    // MARK: - Preferences

    func setAdBlockEnabled(_ enabled: Bool) {
        preferences.adBlockEnabled = enabled
        if !activeTab.isStartPage { emit(.reload) }
    }

    func toggleCurrentDomainWhitelist() {
        let domain = AdBlocker.extractDomain(activeTab.url)
        guard !domain.isEmpty else { return }
        if let idx = preferences.whitelistedDomains.firstIndex(of: domain) {
            preferences.whitelistedDomains.remove(at: idx)
        } else {
            preferences.whitelistedDomains.append(domain)
        }
        emit(.reload)
    }

    func resetAdBlockStats() {
        preferences.totalAdsBlocked = 0
        preferences.totalTrackersBlocked = 0
        showMessage("Статистика блокировщика сброшена")
    }

    func selectSearchEngine(_ engine: SearchEngine) {
        preferences.searchEngine = engine
        showMessage("Поисковая система: \(engine.displayName)")
    }

    func setShowWeather(_ show: Bool) {
        preferences.showWeatherWidget = show
        if show {
            Task { await refreshWeather() }
        }
    }

    func setShowSpeedDial(_ show: Bool) {
        preferences.showSpeedDial = show
    }

    func setAppTheme(_ theme: AppTheme) {
        preferences.appTheme = theme
    }

    func completeOnboarding() {
        preferences.onboardingCompleted = true
    }

    /// Returns true only the first time it is called while the weather widget is on.
    func claimLocationPrompt() -> Bool {
        if UITestLaunch.skipLocation { return false }
        guard preferences.showWeatherWidget, !preferences.locationPromptShown, !locationPromptClaimedThisLaunch else {
            return false
        }
        locationPromptClaimedThisLaunch = true
        preferences.locationPromptShown = true
        return true
    }

    // MARK: - Bookmarks / history / speed dial

    func addBookmark(title: String, rawUrl: String, folder: String) {
        let url = Self.resolveInputToUrl(rawUrl, engine: preferences.searchEngine)
        let item = BookmarkItem(
            id: UUID().uuidString,
            title: title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? url : title,
            url: url,
            folder: folder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Общее" : folder,
            createdAt: Date()
        )
        bookmarks.insert(item, at: 0)
        showMessage("Закладка добавлена")
    }

    func addCurrentPageBookmark() {
        if activeTab.isStartPage {
            showMessage("Откройте сайт, чтобы добавить его в закладки")
            return
        }
        if let match = bookmarks.first(where: { $0.url.compare(activeTab.url, options: .caseInsensitive) == .orderedSame }) {
            bookmarks.removeAll { $0.id == match.id }
            showMessage("Закладка удалена")
        } else {
            addBookmark(title: activeTab.title, rawUrl: activeTab.url, folder: "Избранное")
        }
    }

    func deleteBookmark(_ id: String) {
        bookmarks.removeAll { $0.id == id }
    }

    func addSpeedDial(title: String, rawUrl: String, category: String) {
        let url = Self.resolveInputToUrl(rawUrl, engine: preferences.searchEngine)
        let tile = SpeedDialTile(
            id: UUID().uuidString,
            title: title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? url : title,
            url: url,
            category: category.isEmpty ? "Быстрый доступ" : category,
            position: speedDial.count
        )
        speedDial.append(tile)
        showMessage("Добавлено на стартовую страницу")
    }

    func deleteSpeedDial(_ id: String) {
        speedDial.removeAll { $0.id == id }
    }

    func deleteHistory(_ id: String) {
        history.removeAll { $0.id == id }
    }

    func clearHistory() {
        history.removeAll()
        showMessage("История просмотров очищена")
    }

    func savePassword(siteDomain: String, siteTitle: String, username: String, password: String, notes: String) {
        let entry = PasswordEntry(
            id: UUID().uuidString,
            siteDomain: siteDomain,
            siteTitle: siteTitle,
            username: username,
            password: password,
            notes: notes,
            createdAt: Date()
        )
        passwords.insert(entry, at: 0)
        showMessage("Пароль сохранён")
    }

    func deletePassword(_ id: String) {
        passwords.removeAll { $0.id == id }
    }

    func generateStrongPassword() -> String {
        let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*")
        return String((0..<16).compactMap { _ in alphabet.randomElement() })
    }

    func autofillMatchingSiteForActiveTab() {
        let domain = AdBlocker.extractDomain(activeTab.url)
        guard !domain.isEmpty else {
            showMessage("Откройте сайт, чтобы заполнить пароль")
            return
        }
        guard let match = passwords.first(where: { domain == $0.siteDomain || domain.hasSuffix(".\($0.siteDomain)") }) else {
            showMessage("Для этого сайта нет сохранённого пароля")
            return
        }
        emit(.autofill(username: match.username, password: match.password))
        showMessage("Пароль заполнен")
    }

    func autofillPassword(_ entry: PasswordEntry) {
        emit(.autofill(username: entry.username, password: entry.password))
        showMessage("Пароль заполнен")
        activeSheet = nil
    }

    private func seedSpeedDial() {
        let defaults: [(String, String, String)] = [
            ("Википедия", "https://ru.wikipedia.org", "Энциклопедия"),
            ("Хабр", "https://habr.com/ru/all/", "Технологии"),
            ("GitHub", "https://github.com", "Разработка"),
            ("Kotlin", "https://kotlinlang.org/docs/home.html", "Документация"),
            ("Погода", "https://open-meteo.com", "Метеоданные")
        ]
        speedDial = defaults.enumerated().map { index, item in
            SpeedDialTile(id: UUID().uuidString, title: item.0, url: item.1, category: item.2, position: index)
        }
        preferences.speedDialSeeded = true
    }

    // MARK: - Weather

    func startWeatherLoop() {
        weatherTask?.cancel()
        weatherTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.refreshWeather()
                try? await Task.sleep(nanoseconds: 30 * 60 * 1_000_000_000)
            }
        }
    }

    func refreshWeather() async {
        guard preferences.showWeatherWidget else { return }
        if UITestLaunch.fixtureWeather {
            weather = .success(.screenshotFixture)
            return
        }
        weather = .loading
        do {
            var lat: Double?
            var lon: Double?
            var city: String?
            if preferences.locationPromptShown {
                if let loc = await LocationHelper.shared.request() {
                    lat = loc.coordinate.latitude
                    lon = loc.coordinate.longitude
                    city = "Рядом с вами"
                }
            }
            let forecast = try await WeatherService.fetch(latitude: lat, longitude: lon, cityHint: city)
            weather = .success(forecast)
        } catch {
            weather = .error("Погода недоступна. Проверьте подключение к сети.")
        }
    }

    func requestLocationAndRefresh() {
        preferences.locationPromptShown = true
        Task { await refreshWeather() }
        activeSheet = nil
    }
}

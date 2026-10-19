import SwiftUI

struct TabsSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var showIncognitoOnly = false

    var body: some View {
        let filtered = model.tabs.filter { $0.isIncognito == showIncognitoOnly }
        VStack(alignment: .leading, spacing: 12) {
            Text("Открытые вкладки")
                .font(NeoFont.titleLarge())
                .foregroundColor(palette.text)
                .lineLimit(1)
            HStack(spacing: 8) {
                filterChip("Обычные (\(model.tabs.filter { !$0.isIncognito }.count))", selected: !showIncognitoOnly, tag: "tabs_filter_regular") {
                    showIncognitoOnly = false
                }
                filterChip("Инкогнито (\(model.tabs.filter { $0.isIncognito }.count))", selected: showIncognitoOnly, tag: "tabs_filter_incognito") {
                    showIncognitoOnly = true
                }
            }
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(filtered) { tab in
                        tabCard(tab)
                    }
                }
            }
            .frame(maxHeight: 360)
            HStack(spacing: 10) {
                Button(action: {
                    model.createNewTab(incognito: false)
                    model.activeSheet = nil
                }) {
                    HStack(spacing: 6) {
                        NeoIcon(name: NeoIcons.add, size: 18)
                        Text("Новая вкладка").font(NeoFont.bodyLarge().weight(.semibold))
                    }
                    .foregroundColor(palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .accessibilityIdentifier("new_regular_tab_button")
                Button(action: {
                    model.createNewTab(incognito: true)
                    model.activeSheet = nil
                }) {
                    HStack(spacing: 6) {
                        NeoIcon(name: NeoIcons.visibilityOff, size: 18)
                            .foregroundColor(palette.tertiary)
                        Text("Инкогнито")
                            .font(NeoFont.bodyLarge().weight(.semibold))
                            .foregroundColor(palette.tertiary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(palette.outline, lineWidth: 1))
                }
                .accessibilityIdentifier("new_incognito_tab_button")
            }
            if model.tabs.contains(where: { $0.isIncognito }) {
                Button("Закрыть все вкладки Инкогнито") {
                    model.closeAllIncognito()
                }
                .font(NeoFont.bodyLarge())
                .foregroundColor(palette.tertiary)
                .frame(maxWidth: .infinity)
            }
            Spacer().frame(height: 16)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .onAppear { showIncognitoOnly = model.activeTab.isIncognito }
    }

    private func filterChip(_ title: String, selected: Bool, tag: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(NeoFont.labelLarge())
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(selected ? palette.primaryContainer : Color.clear)
                .foregroundColor(selected ? palette.onPrimaryContainer : palette.text)
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(selected ? palette.primary : palette.outline, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityIdentifier(tag)
    }

    private func tabCard(_ tab: BrowserTab) -> some View {
        let selected = tab.id == model.activeTabId
        let accent = tab.isIncognito ? palette.tertiary : palette.primary
        return Button(action: { model.selectTab(tab.id) }) {
            HStack(spacing: 12) {
                NeoIcon(name: tab.isIncognito ? NeoIcons.visibilityOff : NeoIcons.publicIcon, size: 20)
                    .foregroundColor(accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tab.title)
                        .font(NeoFont.bodyLarge().weight(selected ? .bold : .medium))
                        .foregroundColor(palette.text)
                        .lineLimit(1)
                    Text(tab.url.isEmpty ? "neoserf://start" : tab.url)
                        .font(NeoFont.labelSmall())
                        .foregroundColor(palette.secondaryText)
                        .lineLimit(1)
                }
                Spacer()
                Button(action: { model.closeTab(tab.id) }) {
                    NeoIcon(name: NeoIcons.close, size: 18)
                        .foregroundColor(palette.secondaryText)
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel("Закрыть")
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(selected ? palette.surfaceVariant : palette.background)
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? accent : palette.outline, lineWidth: selected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct ShieldSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette

    var body: some View {
        let domain = AdBlocker.extractDomain(model.activeTab.url)
        let whitelisted = !domain.isEmpty && model.preferences.whitelistedDomains.contains(domain)
        let savedKb = model.preferences.totalAdsBlocked * 38 + model.preferences.totalTrackersBlocked * 14
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    NeoIcon(name: NeoIcons.shield, size: 28)
                        .foregroundColor(palette.primary)
                    Text("Щит приватности NeoSerf")
                        .font(NeoFont.titleLarge())
                        .foregroundColor(palette.text)
                }
                HStack {
                    Text("Блокировка рекламы и трекеров")
                        .font(NeoFont.titleMedium())
                        .foregroundColor(palette.text)
                    Spacer()
                    NeoSwitch(isOn: Binding(
                        get: { model.preferences.adBlockEnabled },
                        set: { model.setAdBlockEnabled($0) }
                    ))
                    .accessibilityIdentifier("adblock_master_switch")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(palette.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                HStack(spacing: 10) {
                    metric("\(model.preferences.totalAdsBlocked)", "Рекламных запросов заблокировано")
                    metric("\(model.preferences.totalTrackersBlocked)", "Аналитических трекеров остановлено")
                }
                metric(savedKb >= 1024 ? String(format: "%.2f МБ", Double(savedKb) / 1024.0) : "\(savedKb) КБ", "Сэкономлено трафика")

                if !domain.isEmpty {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Разрешить рекламу на текущем домене")
                                .font(NeoFont.bodyMedium())
                                .foregroundColor(palette.text)
                            Text(domain)
                                .font(NeoFont.labelSmall())
                                .foregroundColor(palette.primary)
                        }
                        Spacer()
                        NeoSwitch(isOn: Binding(
                            get: { whitelisted },
                            set: { _ in model.toggleCurrentDomainWhitelist() }
                        ))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(palette.outline, lineWidth: 1))
                }

                HStack {
                    Button("Сбросить статистику") { model.resetAdBlockStats() }
                        .font(NeoFont.bodyLarge())
                        .foregroundColor(palette.primary)
                    Spacer()
                    Button(action: { model.activeSheet = nil }) {
                        Text("Закрыть")
                            .font(NeoFont.bodyLarge().weight(.semibold))
                            .foregroundColor(palette.onPrimary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(palette.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
                Spacer().frame(height: 16)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(NeoFont.headlineMedium())
                .foregroundColor(palette.primary)
            Text(label)
                .font(NeoFont.bodyMedium())
                .foregroundColor(palette.secondaryText)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.background)
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(palette.outline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct SettingsSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Настройки")
                    .font(NeoFont.titleLarge())
                    .foregroundColor(palette.text)
                sectionLabel("Оформление")
                HStack(spacing: 8) {
                    ForEach(AppTheme.allCases) { theme in
                        themeTile(theme)
                    }
                }
                sectionLabel("Поисковая система")
                grouped {
                    ForEach(Array(SearchEngine.allCases.enumerated()), id: \.element.id) { index, engine in
                        if index > 0 { divider }
                        Button(action: { model.selectSearchEngine(engine) }) {
                            HStack {
                                Text(engine.displayName)
                                    .font(NeoFont.bodyLarge())
                                    .foregroundColor(palette.text)
                                Spacer()
                                ZStack {
                                    Circle()
                                        .stroke(model.preferences.searchEngine == engine ? palette.primary : palette.secondaryText, lineWidth: 2)
                                        .frame(width: 20, height: 20)
                                    if model.preferences.searchEngine == engine {
                                        Circle()
                                            .fill(palette.primary)
                                            .frame(width: 10, height: 10)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .frame(minHeight: 52)
                        }
                        .buttonStyle(PlainButtonStyle())
                        .accessibilityIdentifier("settings_engine_\(engine.id)")
                    }
                }
                sectionLabel("Стартовая страница")
                grouped {
                    switchRow("Погода", isOn: model.preferences.showWeatherWidget, tag: "settings_weather") {
                        model.setShowWeather($0)
                    }
                    divider
                    switchRow("Быстрый доступ", isOn: model.preferences.showSpeedDial, tag: "settings_shortcuts") {
                        model.setShowSpeedDial($0)
                    }
                }
                grouped {
                    actionRow(NeoIcons.myLocation, "Уточнить местоположение", "menu_location") {
                        model.requestLocationAndRefresh()
                    }
                    divider
                    actionRow(NeoIcons.refresh, "Обновить погоду", "menu_weather_refresh") {
                        Task { await model.refreshWeather() }
                        model.activeSheet = nil
                    }
                }
                .padding(.top, 12)
                Text("Регион определяется по сети. Разрешите местоположение для более точной погоды.")
                    .font(NeoFont.bodySmall())
                    .foregroundColor(palette.secondaryText)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 10)
                Spacer().frame(height: 8)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .accessibilityIdentifier("settings_sheet")
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(NeoFont.bodyMedium())
            .foregroundColor(palette.secondaryText)
            .padding(.top, 20)
            .padding(.bottom, 8)
    }

    private func grouped<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0, content: content)
            .background(palette.surfaceVariant)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var divider: some View {
        Rectangle().fill(palette.outline).frame(height: 1).padding(.horizontal, 16)
    }

    private func themeTile(_ theme: AppTheme) -> some View {
        let selected = model.preferences.appTheme == theme
        return Button(action: { model.setAppTheme(theme) }) {
            VStack(spacing: 6) {
                NeoIcon(name: theme.icon, size: 22)
                Text(theme.title)
                    .font(NeoFont.labelLarge())
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .foregroundColor(selected ? palette.onPrimaryContainer : palette.secondaryText)
            .frame(maxWidth: .infinity, minHeight: 72)
            .padding(.horizontal, 6)
            .padding(.vertical, 12)
            .background(selected ? palette.primaryContainer : Color.clear)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(selected ? palette.primary : palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityIdentifier(theme.testTag)
    }

    private func switchRow(_ title: String, isOn: Bool, tag: String, onChange: @escaping (Bool) -> Void) -> some View {
        HStack {
            Text(title).font(NeoFont.bodyLarge()).foregroundColor(palette.text)
            Spacer()
            NeoSwitch(isOn: Binding(get: { isOn }, set: onChange))
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 52)
        .accessibilityIdentifier(tag)
    }

    private func actionRow(_ icon: String, _ title: String, _ tag: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                NeoIcon(name: icon, size: 20)
                    .foregroundColor(palette.secondaryText)
                Text(title).font(NeoFont.bodyLarge()).foregroundColor(palette.text)
                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityIdentifier(tag)
    }
}

struct BookmarksSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var selectedFolder = "Все"
    @State private var showAdd = false
    @State private var titleInput = ""
    @State private var urlInput = ""
    @State private var folderInput = "Избранное"

    var body: some View {
        let folders = ["Все"] + Array(Set(model.bookmarks.map(\.folder))).sorted()
        let filtered = selectedFolder == "Все" ? model.bookmarks : model.bookmarks.filter { $0.folder == selectedFolder }
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 8) {
                    NeoIcon(name: NeoIcons.bookmark, size: 22).foregroundColor(palette.primary)
                    Text("Синхронизированные закладки")
                        .font(NeoFont.titleLarge())
                        .foregroundColor(palette.text)
                        .lineLimit(1)
                }
                Spacer()
                Button(action: {
                    titleInput = model.activeTab.isStartPage ? "" : model.activeTab.title
                    urlInput = model.activeTab.url
                    folderInput = "Избранное"
                    showAdd = true
                }) {
                    HStack(spacing: 4) {
                        NeoIcon(name: NeoIcons.add, size: 16)
                        Text("Сохранить").font(NeoFont.bodyLarge().weight(.semibold))
                    }
                    .foregroundColor(palette.onPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .accessibilityIdentifier("add_bookmark_dialog_button")
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(folders, id: \.self) { folder in
                        Button(folder) { selectedFolder = folder }
                            .font(NeoFont.labelLarge())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(selectedFolder == folder ? palette.primaryContainer : Color.clear)
                            .foregroundColor(selectedFolder == folder ? palette.onPrimaryContainer : palette.text)
                            .overlay(Capsule().stroke(selectedFolder == folder ? palette.primary : palette.outline, lineWidth: 1))
                            .clipShape(Capsule())
                    }
                }
            }
            if filtered.isEmpty {
                Text("Список закладок пуст. Сохраняйте важные страницы, чтобы они были доступны на всех устройствах.")
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(palette.secondaryText)
                    .padding(.vertical, 24)
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(filtered) { item in
                            Button(action: {
                                model.activeSheet = nil
                                model.submitAddressOrQuery(item.url)
                            }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.title)
                                            .font(NeoFont.titleMedium())
                                            .foregroundColor(palette.text)
                                            .lineLimit(1)
                                        Text(item.url)
                                            .font(NeoFont.labelSmall())
                                            .foregroundColor(palette.primary)
                                            .lineLimit(1)
                                        Text(item.folder)
                                            .font(NeoFont.labelSmall())
                                            .foregroundColor(palette.secondaryText)
                                    }
                                    Spacer()
                                    Button(action: { model.deleteBookmark(item.id) }) {
                                        NeoIcon(name: NeoIcons.delete, size: 18)
                                            .foregroundColor(palette.secondaryText)
                                    }
                                    .accessibilityLabel("Удалить")
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(palette.background)
                                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(palette.outline, lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
                .frame(maxHeight: 380)
            }
            Spacer().frame(height: 18)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .overlay { if showAdd { addDialog } }
    }

    private var addDialog: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { showAdd = false }
            VStack(alignment: .leading, spacing: 10) {
                Text("Добавить текущую страницу")
                    .font(NeoFont.titleLarge())
                    .foregroundColor(palette.text)
                dialogField("Название сайта", text: $titleInput)
                dialogField("Адрес (URL)", text: $urlInput)
                dialogField("Папка", text: $folderInput)
                HStack {
                    Button("Отмена") { showAdd = false }
                        .foregroundColor(palette.primary)
                    Spacer()
                    Button("Сохранить") {
                        if !urlInput.trimmingCharacters(in: .whitespaces).isEmpty {
                            model.addBookmark(title: titleInput, rawUrl: urlInput, folder: folderInput)
                            showAdd = false
                        }
                    }
                    .foregroundColor(palette.onPrimary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            .padding(20)
            .background(palette.surface)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(24)
        }
    }

    private func dialogField(_ label: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(NeoFont.labelLarge()).foregroundColor(palette.secondaryText)
            TextField(label, text: text)
                .font(NeoFont.bodyLarge())
                .foregroundColor(palette.text)
                .padding(10)
                .background(palette.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }
}

struct HistorySheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var query = ""

    var body: some View {
        let filtered = model.history.filter {
            query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) || $0.url.localizedCaseInsensitiveContains(query)
        }
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                HStack(spacing: 8) {
                    NeoIcon(name: NeoIcons.history, size: 22).foregroundColor(palette.primary)
                    Text("История браузера")
                        .font(NeoFont.titleLarge())
                        .foregroundColor(palette.text)
                }
                Spacer()
                Button("Очистить историю") { model.clearHistory() }
                    .font(NeoFont.bodyLarge())
                    .foregroundColor(palette.error)
                    .accessibilityIdentifier("clear_history_button")
            }
            TextField("Поиск по истории...", text: $query)
                .font(NeoFont.bodyLarge())
                .padding(10)
                .background(palette.surfaceVariant)
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(palette.outline, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .accessibilityIdentifier("history_search_input")
            if filtered.isEmpty {
                Text("История посещений пуста.")
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(palette.secondaryText)
                    .padding(.vertical, 24)
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(filtered) { item in
                            Button(action: {
                                model.activeSheet = nil
                                model.submitAddressOrQuery(item.url)
                            }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.title)
                                            .font(NeoFont.bodyMedium().weight(.semibold))
                                            .foregroundColor(palette.text)
                                            .lineLimit(1)
                                        Text(item.url)
                                            .font(NeoFont.labelSmall())
                                            .foregroundColor(palette.primary)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    Button(action: { model.deleteHistory(item.id) }) {
                                        NeoIcon(name: NeoIcons.delete, size: 18)
                                            .foregroundColor(palette.secondaryText)
                                    }
                                    .accessibilityLabel("Удалить")
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(palette.background)
                                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(palette.outline, lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
                .frame(maxHeight: 360)
            }
            Spacer().frame(height: 18)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
}

struct PasswordsSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var showAdd = false
    @State private var revealed: Set<String> = []
    @State private var domain = ""
    @State private var title = ""
    @State private var username = ""
    @State private var password = ""
    @State private var notes = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                HStack(spacing: 8) {
                    NeoIcon(name: NeoIcons.lock, size: 22).foregroundColor(palette.primary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Защищенный менеджер паролей")
                            .font(NeoFont.titleLarge())
                            .foregroundColor(palette.text)
                        Text("Пароли шифруются алгоритмом AES-GCM перед синхронизацией в облаке Google")
                            .font(NeoFont.labelSmall())
                            .foregroundColor(palette.primary)
                    }
                }
                Spacer()
                Button(action: {
                    domain = AdBlocker.extractDomain(model.activeTab.url)
                    title = domain
                    username = ""
                    password = ""
                    notes = ""
                    showAdd = true
                }) {
                    HStack(spacing: 4) {
                        NeoIcon(name: NeoIcons.add, size: 16)
                        Text("Сохранить").font(NeoFont.bodyLarge().weight(.semibold))
                    }
                    .foregroundColor(palette.onPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .accessibilityIdentifier("add_password_button")
            }
            if model.passwords.isEmpty {
                Text("В сейфе пока нет сохраненных паролей.")
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(palette.secondaryText)
                    .padding(.vertical, 24)
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(model.passwords) { entry in
                            passwordCard(entry)
                        }
                    }
                }
                .frame(maxHeight: 380)
            }
            Spacer().frame(height: 18)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .overlay { if showAdd { addDialog } }
    }

    private func passwordCard(_ entry: PasswordEntry) -> some View {
        let shown = revealed.contains(entry.id)
        return VStack(alignment: .leading, spacing: 6) {
            Text(entry.siteTitle).font(NeoFont.titleMedium()).foregroundColor(palette.text)
            Text(entry.siteDomain).font(NeoFont.labelSmall()).foregroundColor(palette.primary)
            Text(entry.username).font(NeoFont.bodyMedium()).foregroundColor(palette.text)
            Text(shown ? entry.password : "••••••••••••")
                .font(NeoFont.labelSmall())
                .foregroundColor(palette.secondaryText)
            HStack {
                Button("Показать или скрыть пароль") {
                    if shown { revealed.remove(entry.id) } else { revealed.insert(entry.id) }
                }
                Spacer()
                Button("Заполнить на странице") { model.autofillPassword(entry) }
                Button(action: { model.deletePassword(entry.id) }) {
                    NeoIcon(name: NeoIcons.delete, size: 18).foregroundColor(palette.secondaryText)
                }
                .accessibilityLabel("Удалить")
            }
            .font(NeoFont.labelLarge())
            .foregroundColor(palette.primary)
        }
        .padding(14)
        .background(palette.background)
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(palette.outline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var addDialog: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { showAdd = false }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Добавить пароль").font(NeoFont.titleLarge()).foregroundColor(palette.text)
                    dialogField("Домен сайта (например, github.com)", text: $domain)
                    dialogField("Название сервиса", text: $title)
                    dialogField("Логин или Email", text: $username)
                    dialogField("Пароль", text: $password)
                    Button(action: { password = model.generateStrongPassword() }) {
                        HStack(spacing: 6) {
                            NeoIcon(name: NeoIcons.autoFixHigh, size: 16)
                            Text("Сгенерировать надежный пароль")
                        }
                        .font(NeoFont.bodyLarge())
                        .foregroundColor(palette.primary)
                        .frame(maxWidth: .infinity)
                        .padding(10)
                        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(palette.outline, lineWidth: 1))
                    }
                    dialogField("Заметка (необязательно)", text: $notes)
                    HStack {
                        Button("Отмена") { showAdd = false }.foregroundColor(palette.primary)
                        Spacer()
                        Button("Сохранить") {
                            if !username.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty {
                                model.savePassword(
                                    siteDomain: domain.isEmpty ? "neoserf.app" : domain,
                                    siteTitle: title.isEmpty ? domain : title,
                                    username: username,
                                    password: password,
                                    notes: notes
                                )
                                showAdd = false
                            }
                        }
                        .foregroundColor(palette.onPrimary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(palette.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
                .padding(20)
            }
            .background(palette.surface)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(24)
            .frame(maxHeight: 520)
        }
    }

    private func dialogField(_ label: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(NeoFont.labelLarge()).foregroundColor(palette.secondaryText)
            TextField(label, text: text)
                .font(NeoFont.bodyLarge())
                .foregroundColor(palette.text)
                .padding(10)
                .background(palette.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }
}

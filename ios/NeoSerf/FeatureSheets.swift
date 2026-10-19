import SwiftUI

struct TabsSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var showIncognitoOnly = false

    var body: some View {
        let filtered = model.tabs.filter { $0.isIncognito == showIncognitoOnly }
        NavigationView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    filterChip(
                        title: "Обычные (\(model.tabs.filter { !$0.isIncognito }.count))",
                        selected: !showIncognitoOnly
                    ) { showIncognitoOnly = false }
                    filterChip(
                        title: "Инкогнито (\(model.tabs.filter { $0.isIncognito }.count))",
                        selected: showIncognitoOnly
                    ) { showIncognitoOnly = true }
                }
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(filtered) { tab in
                            tabCard(tab)
                        }
                    }
                }
                HStack(spacing: 10) {
                    Button(action: {
                        model.createNewTab(incognito: false)
                        model.activeSheet = nil
                    }) {
                        Label("Новая вкладка", systemImage: "plus")
                            .font(.system(size: 14, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(palette.primary)
                            .foregroundColor(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    Button(action: {
                        model.createNewTab(incognito: true)
                        model.activeSheet = nil
                    }) {
                        Label("Инкогнито", systemImage: "eye.slash")
                            .font(.system(size: 14, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.outline, lineWidth: 1))
                            .foregroundColor(palette.tertiary)
                    }
                }
                if model.tabs.contains(where: { $0.isIncognito }) {
                    Button("Закрыть все вкладки Инкогнито") {
                        model.closeAllIncognito()
                    }
                    .foregroundColor(palette.tertiary)
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(20)
            .background(palette.surface.ignoresSafeArea())
            .navigationTitle("Открытые вкладки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { model.activeSheet = nil }
                }
            }
        }
        .onAppear {
            showIncognitoOnly = model.activeTab.isIncognito
        }
    }

    private func filterChip(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .medium))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(selected ? palette.primaryContainer : Color.clear)
                .foregroundColor(selected ? palette.onPrimaryContainer : palette.text)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? palette.primary : palette.outline, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func tabCard(_ tab: BrowserTab) -> some View {
        let selected = tab.id == model.activeTabId
        let accent = tab.isIncognito ? palette.tertiary : palette.primary
        return Button(action: { model.selectTab(tab.id) }) {
            HStack(spacing: 12) {
                Image(systemName: tab.isIncognito ? "eye.slash" : "globe")
                    .foregroundColor(accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tab.title)
                        .font(.system(size: 16, weight: selected ? .bold : .medium))
                        .foregroundColor(palette.text)
                        .lineLimit(1)
                    Text(tab.url.isEmpty ? "neoserf://start" : tab.url)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(palette.secondaryText)
                        .lineLimit(1)
                }
                Spacer()
                Button(action: { model.closeTab(tab.id) }) {
                    Image(systemName: "xmark")
                        .foregroundColor(palette.secondaryText)
                        .frame(width: 36, height: 36)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(selected ? palette.surfaceVariant : palette.background)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(selected ? accent : palette.outline, lineWidth: selected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
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
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        Image(systemName: "shield.fill")
                            .font(.system(size: 24))
                            .foregroundColor(palette.primary)
                        Text("Щит приватности NeoSerf")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(palette.text)
                    }
                    HStack {
                        Text("Блокировка рекламы и трекеров")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(palette.text)
                        Spacer()
                        Toggle("", isOn: Binding(
                            get: { model.preferences.adBlockEnabled },
                            set: { model.setAdBlockEnabled($0) }
                        ))
                        .labelsHidden()
                        .accentColor(palette.primary)
                    }
                    .padding(16)
                    .background(palette.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    HStack(spacing: 10) {
                        metric("\(model.preferences.totalAdsBlocked)", "Рекламных запросов заблокировано")
                        metric("\(model.preferences.totalTrackersBlocked)", "Аналитических трекеров остановлено")
                    }
                    metric(savedKb >= 1024 ? String(format: "%.2f МБ", Double(savedKb) / 1024.0) : "\(savedKb) КБ", "Сэкономлено трафика")

                    if !domain.isEmpty {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Разрешить рекламу на текущем домене")
                                    .font(.system(size: 14))
                                    .foregroundColor(palette.text)
                                Text(domain)
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundColor(palette.primary)
                            }
                            Spacer()
                            Toggle("", isOn: Binding(
                                get: { whitelisted },
                                set: { _ in model.toggleCurrentDomainWhitelist() }
                            ))
                            .labelsHidden()
                            .accentColor(palette.primary)
                        }
                        .padding(16)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(palette.outline, lineWidth: 1))
                    }

                    HStack {
                        Button("Сбросить статистику") { model.resetAdBlockStats() }
                            .foregroundColor(palette.primary)
                        Spacer()
                        Button("Закрыть") { model.activeSheet = nil }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(palette.primary)
                            .foregroundColor(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
                .padding(20)
            }
            .background(palette.surface.ignoresSafeArea())
            .navigationBarHidden(true)
        }
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(palette.primary)
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(palette.secondaryText)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.background)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(palette.outline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

struct SettingsSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Настройки")
                        .font(.system(size: 22, weight: .semibold))
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
                                        .foregroundColor(palette.text)
                                    Spacer()
                                    if model.preferences.searchEngine == engine {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundColor(palette.primary)
                                    } else {
                                        Image(systemName: "circle")
                                            .foregroundColor(palette.secondaryText)
                                    }
                                }
                                .padding(.horizontal, 16)
                                .frame(minHeight: 52)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    sectionLabel("Стартовая страница")
                    grouped {
                        switchRow("Погода", isOn: model.preferences.showWeatherWidget) {
                            model.setShowWeather($0)
                        }
                        divider
                        switchRow("Быстрый доступ", isOn: model.preferences.showSpeedDial) {
                            model.setShowSpeedDial($0)
                        }
                    }
                    grouped {
                        actionRow(system: "location", title: "Уточнить местоположение") {
                            model.requestLocationAndRefresh()
                        }
                        divider
                        actionRow(system: "arrow.clockwise", title: "Обновить погоду") {
                            Task { await model.refreshWeather() }
                            model.activeSheet = nil
                        }
                    }
                    .padding(.top, 12)
                    Text("Регион определяется по сети. Разрешите местоположение для более точной погоды.")
                        .font(.system(size: 12))
                        .foregroundColor(palette.secondaryText)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 10)
                }
                .padding(20)
            }
            .background(palette.surface.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { model.activeSheet = nil }
                }
            }
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundColor(palette.secondaryText)
            .padding(.top, 16)
            .padding(.bottom, 8)
    }

    private func grouped<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0, content: content)
            .background(palette.surfaceVariant)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var divider: some View {
        Rectangle().fill(palette.outline).frame(height: 1).padding(.horizontal, 16)
    }

    private func themeTile(_ theme: AppTheme) -> some View {
        let selected = model.preferences.appTheme == theme
        return Button(action: { model.setAppTheme(theme) }) {
            VStack(spacing: 6) {
                Image(systemName: theme.icon)
                    .font(.system(size: 20))
                Text(theme.title)
                    .font(.system(size: 13, weight: .medium))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .foregroundColor(selected ? palette.onPrimaryContainer : palette.secondaryText)
            .frame(maxWidth: .infinity, minHeight: 72)
            .padding(.vertical, 12)
            .background(selected ? palette.primaryContainer : Color.clear)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? palette.primary : palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func switchRow(_ title: String, isOn: Bool, onChange: @escaping (Bool) -> Void) -> some View {
        Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
            Text(title).foregroundColor(palette.text)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 52)
        .accentColor(palette.primary)
    }

    private func actionRow(system: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: system)
                    .foregroundColor(palette.secondaryText)
                    .frame(width: 20)
                Text(title).foregroundColor(palette.text)
                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct BookmarksSheet: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var selectedFolder = "Все"
    @State private var showAdd = false

    var body: some View {
        let folders = ["Все"] + Array(Set(model.bookmarks.map(\.folder))).sorted()
        let filtered = selectedFolder == "Все" ? model.bookmarks : model.bookmarks.filter { $0.folder == selectedFolder }
        NavigationView {
            VStack(alignment: .leading, spacing: 10) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(folders, id: \.self) { folder in
                            Button(folder) { selectedFolder = folder }
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
                    Text("Список закладок пуст. Сохраняйте важные страницы, чтобы они были доступны позже.")
                        .font(.system(size: 14))
                        .foregroundColor(palette.secondaryText)
                        .padding(.vertical, 24)
                    Spacer()
                } else {
                    List {
                        ForEach(filtered) { item in
                            Button(action: {
                                model.activeSheet = nil
                                model.submitAddressOrQuery(item.url)
                            }) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.title)
                                        .foregroundColor(palette.text)
                                        .lineLimit(1)
                                    Text(item.url)
                                        .font(.system(size: 12))
                                        .foregroundColor(palette.secondaryText)
                                        .lineLimit(1)
                                }
                            }
                            .swipeActions {
                                Button(role: .destructive) { model.deleteBookmark(item.id) } label: {
                                    Label("Удалить", systemImage: "trash")
                                }
                            }
                        }
                    }
                    .listStyle(PlainListStyle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .background(palette.surface.ignoresSafeArea())
            .navigationTitle("Закладки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { model.activeSheet = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        if !model.activeTab.isStartPage {
                            model.addCurrentPageBookmark()
                        } else {
                            showAdd = true
                        }
                    }) {
                        Label("Сохранить", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $showAdd) {
                AddBookmarkSheet { title, url, folder in
                    model.addBookmark(title: title, rawUrl: url, folder: folder)
                    showAdd = false
                } onCancel: { showAdd = false }
            }
        }
    }
}

private struct AddBookmarkSheet: View {
    var onSave: (String, String, String) -> Void
    var onCancel: () -> Void
    @State private var title = ""
    @State private var url = ""
    @State private var folder = "Избранное"

    var body: some View {
        NavigationView {
            Form {
                TextField("Название сайта", text: $title)
                TextField("Адрес (URL)", text: $url)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                TextField("Папка", text: $folder)
            }
            .navigationTitle("Новая закладка")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена", action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        if !url.trimmingCharacters(in: .whitespaces).isEmpty {
                            onSave(title, url, folder)
                        }
                    }
                }
            }
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
        NavigationView {
            VStack {
                TextField("Поиск по истории...", text: $query)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal, 16)
                if filtered.isEmpty {
                    Text("История посещений пуста.")
                        .foregroundColor(palette.secondaryText)
                        .padding(.top, 24)
                    Spacer()
                } else {
                    List {
                        ForEach(filtered) { item in
                            Button(action: {
                                model.activeSheet = nil
                                model.submitAddressOrQuery(item.url)
                            }) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.title)
                                        .foregroundColor(palette.text)
                                        .lineLimit(1)
                                    Text(item.url)
                                        .font(.system(size: 12))
                                        .foregroundColor(palette.secondaryText)
                                        .lineLimit(1)
                                }
                            }
                            .swipeActions {
                                Button(role: .destructive) { model.deleteHistory(item.id) } label: {
                                    Label("Удалить", systemImage: "trash")
                                }
                            }
                        }
                    }
                    .listStyle(PlainListStyle())
                }
            }
            .background(palette.surface.ignoresSafeArea())
            .navigationTitle("История браузера")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { model.activeSheet = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Очистить историю") { model.clearHistory() }
                        .foregroundColor(palette.error)
                }
            }
        }
    }
}

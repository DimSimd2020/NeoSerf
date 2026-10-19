import SwiftUI

struct TopOmniboxBar: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var text: String = ""
    @FocusState private var focused: Bool

    var body: some View {
        let tab = model.activeTab
        let accent = tab.isIncognito ? palette.tertiary : palette.primary
        VStack(spacing: 0) {
            HStack(spacing: 2) {
                HStack(spacing: 6) {
                    shieldBadge(tab: tab, accent: accent)
                    ZStack(alignment: .leading) {
                        if text.isEmpty {
                            Text(tab.isIncognito ? "Приватный поиск или адрес сайта" : "Введите адрес сайта или поисковый запрос")
                                .font(.system(size: 14))
                                .foregroundColor(palette.secondaryText)
                                .lineLimit(1)
                        }
                        TextField("", text: $text)
                            .font(.system(size: 14))
                            .foregroundColor(palette.text)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                            .submitLabel(.go)
                            .focused($focused)
                            .onSubmit { submit() }
                    }
                    if focused && !text.isEmpty {
                        Button(action: { text = "" }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(palette.secondaryText)
                                .frame(width: 32, height: 32)
                        }
                        .accessibilityLabel("Очистить ввод")
                    } else if !tab.isStartPage {
                        Button(action: { model.reload() }) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 14))
                                .foregroundColor(palette.secondaryText)
                                .frame(width: 32, height: 32)
                        }
                        .accessibilityLabel("Обновить страницу")
                    }
                }
                .padding(.horizontal, 10)
                .frame(height: 44)
                .background(palette.surfaceVariant)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(focused ? accent : palette.outline, lineWidth: focused ? 1.5 : 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14))

                BrowserTopActions(
                    isBookmarked: model.isCurrentUrlBookmarked,
                    onOpenBookmarks: { model.activeSheet = .bookmarks },
                    onOpenMenu: { model.menuExpanded.toggle() }
                )
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)

            if tab.isLoading {
                ProgressView(value: min(max(tab.progress, 0.05), 1))
                    .progressViewStyle(LinearProgressViewStyle(tint: accent))
                    .frame(height: 2.5)
            }
            Rectangle()
                .fill(palette.outline.opacity(0.6))
                .frame(height: 1)
        }
        .background(palette.surface)
        .onAppear { text = tab.url }
        .onChange(of: tab.url) { newValue in
            if !focused { text = newValue }
        }
        .onChange(of: tab.id) { _ in
            text = model.activeTab.url
            focused = false
        }
    }

    private func shieldBadge(tab: BrowserTab, accent: Color) -> some View {
        let blocked = tab.adsBlockedInTab + tab.trackersBlockedInTab
        return Button(action: { model.activeSheet = .shield }) {
            HStack(spacing: 3) {
                Image(systemName: model.preferences.adBlockEnabled ? "shield.fill" : "lock.shield")
                    .font(.system(size: 14))
                    .foregroundColor(model.preferences.adBlockEnabled ? palette.primary : palette.secondaryText)
                if blocked > 0 {
                    Text("\(blocked)")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(palette.primary)
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 4)
        }
        .accessibilityLabel("Щит блокировщика рекламы")
    }

    private func submit() {
        focused = false
        model.submitAddressOrQuery(text)
    }
}

struct BrowserTopActions: View {
    let isBookmarked: Bool
    let onOpenBookmarks: () -> Void
    let onOpenMenu: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onOpenBookmarks) {
                Image(systemName: isBookmarked ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 17))
                    .foregroundColor(isBookmarked ? palette.primary : palette.secondaryText)
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel("Закладки")
            Button(action: onOpenMenu) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(palette.text)
                    .frame(width: 40, height: 40)
                    .rotationEffect(.degrees(90))
            }
            .accessibilityLabel("Меню")
        }
    }
}

struct BrowserMenuPopup: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette

    var body: some View {
        let tab = model.activeTab
        VStack(alignment: .leading, spacing: 0) {
            menuRow(tabCount: model.tabs.count, incognito: tab.isIncognito) {
                model.menuExpanded = false
                model.activeSheet = .tabs
            }
            iconRow(system: "plus", title: "Новая вкладка") {
                model.createNewTab(incognito: false)
            }
            iconRow(system: "eye.slash", title: "Вкладка инкогнито") {
                model.createNewTab(incognito: true)
            }
            divider
            iconRow(system: "clock", title: "История") {
                model.menuExpanded = false
                model.activeSheet = .history
            }
            if !tab.isStartPage {
                iconRow(system: "chevron.left", title: "Назад", enabled: tab.canGoBack || !tab.isStartPage) {
                    model.menuExpanded = false
                    model.goBack()
                }
                iconRow(system: "chevron.right", title: "Вперед", enabled: tab.canGoForward) {
                    model.menuExpanded = false
                    model.goForward()
                }
                toggleRow("Версия для ПК", isOn: tab.isDesktopMode) {
                    model.menuExpanded = false
                    model.toggleDesktopMode()
                }
                toggleRow("Режим чтения", isOn: tab.isReaderMode) {
                    model.menuExpanded = false
                    model.toggleReaderMode()
                }
            }
            iconRow(system: "shield", title: "Защита") {
                model.menuExpanded = false
                model.activeSheet = .shield
            }
            divider
            iconRow(system: "slider.horizontal.3", title: "Настройки") {
                model.menuExpanded = false
                model.activeSheet = .settings
            }
        }
        .padding(.vertical, 10)
        .frame(width: 280)
        .background(palette.surfaceVariant)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(palette.outline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .shadow(color: Color.black.opacity(0.35), radius: 16, x: 0, y: 8)
        .padding(.trailing, 12)
        .padding(.top, 8)
    }

    private var divider: some View {
        Rectangle()
            .fill(palette.outline)
            .frame(height: 1)
            .padding(.horizontal, 18)
            .padding(.vertical, 6)
    }

    private func iconRow(system: String, title: String, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: system)
                    .font(.system(size: 16))
                    .foregroundColor(palette.secondaryText)
                    .frame(width: 20)
                Text(title)
                    .font(.system(size: 16))
                    .foregroundColor(palette.text)
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
        .buttonStyle(PlainButtonStyle())
    }

    private func menuRow(tabCount: Int, incognito: Bool, action: @escaping () -> Void) -> some View {
        let accent = incognito ? palette.tertiary : palette.primary
        return Button(action: action) {
            HStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(accent, lineWidth: 1.5)
                    Text("\(tabCount)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(accent)
                }
                .frame(width: 20, height: 20)
                Text("Вкладки")
                    .font(.system(size: 16))
                    .foregroundColor(palette.text)
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func toggleRow(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 15))
                .foregroundColor(palette.text)
            Spacer()
            Toggle("", isOn: Binding(get: { isOn }, set: { _ in action() }))
                .labelsHidden()
                .accentColor(palette.primary)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 48)
    }
}

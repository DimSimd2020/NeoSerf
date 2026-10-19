import SwiftUI

struct TopOmniboxBar: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var text: String = ""
    @FocusState private var focused: Bool
    @State private var submitInFlight = false

    var body: some View {
        let tab = model.activeTab
        let accent = tab.isIncognito ? palette.tertiary : palette.primary
        VStack(spacing: 0) {
            HStack(spacing: 2) {
                HStack(spacing: 6) {
                    shieldBadge(tab: tab)
                    ZStack(alignment: .leading) {
                        if text.isEmpty {
                            Text(tab.isIncognito ? "Приватный поиск или адрес сайта" : "Введите адрес сайта или поисковый запрос")
                                .font(NeoFont.bodyMedium())
                                .foregroundColor(palette.secondaryText)
                                .lineLimit(1)
                        }
                        TextField("", text: $text)
                            .font(NeoFont.bodyMedium())
                            .foregroundColor(palette.text)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                            .submitLabel(.go)
                            .focused($focused)
                            .onSubmit { submit() }
                            .accessibilityIdentifier("omnibox_input")
                    }
                    if focused && !text.isEmpty {
                        Button(action: { text = "" }) {
                            NeoIcon(name: NeoIcons.close, size: 16)
                                .foregroundColor(palette.secondaryText)
                                .frame(width: 32, height: 32)
                        }
                        .accessibilityLabel("Очистить ввод")
                        .accessibilityIdentifier("omnibox_clear_button")
                    } else if !tab.isStartPage {
                        Button(action: { model.reload() }) {
                            NeoIcon(name: NeoIcons.refresh, size: 17)
                                .foregroundColor(palette.secondaryText)
                                .frame(width: 32, height: 32)
                        }
                        .accessibilityLabel("Обновить страницу")
                        .accessibilityIdentifier("omnibox_reload_button")
                    }
                }
                .padding(.horizontal, 10)
                .frame(height: 44)
                .background(palette.surfaceVariant)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(focused ? accent : palette.outline, lineWidth: focused ? 1.5 : 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

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
            submitInFlight = false
        }
        .onChange(of: focused) { isFocused in
            if isFocused { submitInFlight = false }
        }
    }

    private func shieldBadge(tab: BrowserTab) -> some View {
        let blocked = tab.adsBlockedInTab + tab.trackersBlockedInTab
        return Button(action: { model.activeSheet = .shield }) {
            HStack(spacing: 3) {
                NeoIcon(
                    name: model.preferences.adBlockEnabled ? NeoIcons.shield : NeoIcons.security,
                    size: 18
                )
                .foregroundColor(model.preferences.adBlockEnabled ? palette.primary : palette.secondaryText)
                if blocked > 0 {
                    Text("\(blocked)")
                        .font(NeoFont.labelSmall().weight(.bold))
                        .foregroundColor(palette.primary)
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 4)
        }
        .accessibilityLabel("Щит блокировщика рекламы")
        .accessibilityIdentifier("omnibox_shield_badge")
    }

    private func submit() {
        if submitInFlight { return }
        submitInFlight = true
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
                NeoIcon(name: isBookmarked ? NeoIcons.bookmark : NeoIcons.bookmarkBorder, size: 20)
                    .foregroundColor(isBookmarked ? palette.primary : palette.secondaryText)
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel("Закладки")
            .accessibilityIdentifier("nav_bookmarks_button")
            Button(action: onOpenMenu) {
                NeoIcon(name: NeoIcons.moreVert, size: 20)
                    .foregroundColor(palette.text)
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel("Меню")
            .accessibilityIdentifier("nav_menu_button")
        }
    }
}

struct BrowserMenuPopup: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette

    var body: some View {
        let tab = model.activeTab
        VStack(alignment: .leading, spacing: 0) {
            tabsRow
            iconRow(NeoIcons.add, "Новая вкладка", "menu_new_tab") {
                model.createNewTab(incognito: false)
            }
            iconRow(NeoIcons.visibilityOff, "Вкладка инкогнито", "menu_incognito") {
                model.createNewTab(incognito: true)
            }
            divider
            iconRow(NeoIcons.history, "История", "menu_item_history") {
                choose { model.activeSheet = .history }
            }
            iconRow(NeoIcons.key, "Пароли", "menu_item_passwords") {
                choose { model.activeSheet = .passwords }
            }
            if !tab.isStartPage {
                iconRow(NeoIcons.password, "Заполнить пароль", "menu_item_autofill") {
                    choose { model.autofillMatchingSiteForActiveTab() }
                }
                toggleRow("Версия для ПК", isOn: tab.isDesktopMode, tag: "menu_switch_desktop") {
                    choose { model.toggleDesktopMode() }
                }
                toggleRow("Режим чтения", isOn: tab.isReaderMode, tag: "menu_switch_reader") {
                    choose { model.toggleReaderMode() }
                }
            }
            iconRow(NeoIcons.shield, "Защита", "menu_item_shield") {
                choose { model.activeSheet = .shield }
            }
            divider
            iconRow(NeoIcons.tune, "Настройки", "menu_settings") {
                choose { model.activeSheet = .settings }
            }
        }
        .padding(.vertical, 10)
        .frame(width: 304)
        .background(palette.surfaceVariant)
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(palette.outline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: Color.black.opacity(0.28), radius: 16, x: 0, y: 8)
        .padding(.trailing, 12)
        .padding(.top, 40)
        .accessibilityIdentifier("browser_overflow_menu")
    }

    private func choose(_ action: @escaping () -> Void) {
        model.menuExpanded = false
        action()
    }

    private var divider: some View {
        Rectangle()
            .fill(palette.outline)
            .frame(height: 1)
            .padding(.horizontal, 18)
            .padding(.vertical, 6)
    }

    private var tabsRow: some View {
        let accent = model.activeTab.isIncognito ? palette.tertiary : palette.primary
        return Button(action: { choose { model.activeSheet = .tabs } }) {
            HStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .stroke(accent, lineWidth: 1.5)
                    Text("\(model.tabs.count)")
                        .font(NeoFont.labelSmall().weight(.bold))
                        .foregroundColor(accent)
                }
                .frame(width: 20, height: 20)
                Text("Вкладки")
                    .font(NeoFont.bodyLarge())
                    .foregroundColor(palette.text)
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityIdentifier("menu_tabs")
    }

    private func iconRow(_ icon: String, _ title: String, _ tag: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                NeoIcon(name: icon, size: 20)
                    .foregroundColor(palette.secondaryText)
                Text(title)
                    .font(NeoFont.bodyLarge())
                    .foregroundColor(palette.text)
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityIdentifier(tag)
    }

    private func toggleRow(_ title: String, isOn: Bool, tag: String, action: @escaping () -> Void) -> some View {
        HStack {
            Text(title)
                .font(NeoFont.bodyMedium())
                .foregroundColor(palette.text)
            Spacer()
            NeoSwitch(isOn: Binding(get: { isOn }, set: { _ in action() }))
                .accessibilityIdentifier(tag)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 48)
    }
}

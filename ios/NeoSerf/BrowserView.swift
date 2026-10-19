import SwiftUI

struct BrowserView: View {
    @StateObject var model = BrowserViewModel()
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        let scheme = model.preferences.appTheme.colorScheme ?? systemScheme
        let palette = Palette.current(colorScheme: scheme)
        Group {
            if !model.preferences.onboardingCompleted {
                WelcomeView(model: model)
            } else {
                browserChrome
            }
        }
        .background(palette.background.ignoresSafeArea())
        .environment(\.palette, palette)
        .environment(\.locale, Locale(identifier: "ru"))
        .preferredColorScheme(forcedScheme)
        .onAppear { requestLocationIfNeeded() }
        .onChange(of: model.preferences.onboardingCompleted) { done in
            if done { requestLocationIfNeeded() }
        }
    }

    private var forcedScheme: ColorScheme? {
        if let appearance = UITestLaunch.appearance {
            return appearance == "dark" ? .dark : .light
        }
        return model.preferences.appTheme.colorScheme
    }

    private var browserChrome: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                if !model.activeTab.isStartPage {
                    TopOmniboxBar(model: model)
                }
                ZStack(alignment: .topTrailing) {
                    content
                        .accessibilityIdentifier("browser_content")
                    if model.activeTab.isStartPage {
                        BrowserTopActions(
                            isBookmarked: false,
                            onOpenBookmarks: { model.activeSheet = .bookmarks },
                            onOpenMenu: { model.menuExpanded.toggle() }
                        )
                        .padding(.trailing, 10)
                        .padding(.top, 8)
                    }
                }
            }
            if model.menuExpanded {
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .onTapGesture { model.menuExpanded = false }
                BrowserMenuPopup(model: model)
            }
            if let sheet = model.activeSheet {
                NeoBottomSheet(onDismiss: { model.activeSheet = nil }) {
                    sheetContent(sheet)
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let message = model.statusMessage {
                Text(message)
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(Palette.current(colorScheme: model.preferences.appTheme.colorScheme ?? systemScheme).text)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Palette.current(colorScheme: model.preferences.appTheme.colorScheme ?? systemScheme).surfaceVariant)
                    .clipShape(Capsule())
                    .padding(.bottom, 24)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
                            if model.statusMessage == message {
                                model.statusMessage = nil
                            }
                        }
                    }
            }
        }
    }

    @ViewBuilder
    private func sheetContent(_ sheet: ActiveSheet) -> some View {
        switch sheet {
        case .tabs: TabsSheet(model: model)
        case .shield: ShieldSheet(model: model)
        case .settings: SettingsSheet(model: model)
        case .bookmarks: BookmarksSheet(model: model)
        case .history: HistorySheet(model: model)
        case .passwords: PasswordsSheet(model: model)
        }
    }

    @ViewBuilder
    private var content: some View {
        let tab = model.activeTab
        ZStack {
            WebViewHost(model: model)
                .opacity(tab.isStartPage || tab.isReaderMode ? 0 : 1)
                .allowsHitTesting(!(tab.isStartPage || tab.isReaderMode))
            if tab.isStartPage {
                StartPageView(model: model)
            } else if tab.isReaderMode {
                ReaderView(
                    article: tab.readerArticle,
                    fontSize: model.preferences.readerFontSize,
                    theme: model.preferences.readerTheme,
                    onClose: { model.toggleReaderMode() },
                    onFont: { model.preferences.readerFontSize = $0 },
                    onTheme: { model.preferences.readerTheme = $0 }
                )
            }
        }
    }

    private func requestLocationIfNeeded() {
        guard model.preferences.onboardingCompleted else { return }
        if model.claimLocationPrompt() {
            Task { await LocationHelper.shared.request() }
            Task { await model.refreshWeather() }
        }
    }
}

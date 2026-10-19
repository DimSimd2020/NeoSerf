import SwiftUI

private let welcomePageCount = 2

struct WelcomeView: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var page = 0

    var body: some View {
        VStack(spacing: 0) {
            Group {
                if page == 0 {
                    themePage
                } else {
                    searchPage
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            HStack {
                Group {
                    if page > 0 {
                        Button(action: { page = 0 }) {
                            HStack(spacing: 6) {
                                NeoIcon(name: NeoIcons.arrowBack, size: 18)
                                Text("Назад").font(NeoFont.bodyLarge())
                            }
                            .foregroundColor(palette.primary)
                        }
                        .accessibilityIdentifier("welcome_back")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                stepDots
                Group {
                    Button(action: {
                        if page + 1 < welcomePageCount {
                            page += 1
                        } else {
                            model.completeOnboarding()
                        }
                    }) {
                        Text(page + 1 < welcomePageCount ? "Далее" : "Начать")
                            .font(NeoFont.bodyLarge().weight(.semibold))
                            .foregroundColor(palette.onPrimary)
                            .frame(height: 48)
                            .padding(.horizontal, 22)
                            .background(palette.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .accessibilityIdentifier("welcome_next")
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .frame(maxWidth: 480)
            .padding(.top, 12)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.background.ignoresSafeArea())
        .accessibilityIdentifier("welcome_screen")
    }

    private var themePage: some View {
        ScrollView {
            VStack(spacing: 0) {
                FinLogo(size: 88)
                Spacer().frame(height: 18)
                heading(
                    "Добро пожаловать в NeoSerf",
                    "Выберите, как будет выглядеть браузер. Это можно изменить в настройках."
                )
                Spacer().frame(height: 28)
                ForEach(AppTheme.allCases) { theme in
                    choiceCard(
                        title: theme.title,
                        subtitle: nil,
                        icon: theme.icon,
                        selected: model.preferences.appTheme == theme,
                        tag: theme.welcomeTag
                    ) {
                        model.setAppTheme(theme)
                    }
                    Spacer().frame(height: 10)
                }
            }
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
    }

    private var searchPage: some View {
        ScrollView {
            VStack(spacing: 0) {
                heading("Поиск по умолчанию", "Что открывать, когда вы ищете в адресной строке.")
                Spacer().frame(height: 24)
                ForEach(SearchEngine.allCases) { engine in
                    choiceCard(
                        title: engine.displayName,
                        subtitle: engine.description,
                        icon: NeoIcons.search,
                        selected: model.preferences.searchEngine == engine,
                        tag: "welcome_engine_\(engine.id)"
                    ) {
                        model.selectSearchEngine(engine)
                    }
                    Spacer().frame(height: 10)
                }
            }
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
    }

    private func heading(_ title: String, _ subtitle: String) -> some View {
        VStack(spacing: 8) {
            Text(title)
                .font(NeoFont.headlineSmall())
                .foregroundColor(palette.text)
                .multilineTextAlignment(.center)
            Text(subtitle)
                .font(NeoFont.bodyMedium())
                .foregroundColor(palette.secondaryText)
                .multilineTextAlignment(.center)
        }
    }

    private func choiceCard(title: String, subtitle: String?, icon: String, selected: Bool, tag: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                NeoIcon(name: icon, size: 22)
                    .foregroundColor(selected ? palette.primary : palette.secondaryText)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(selected ? NeoFont.titleMedium() : NeoFont.bodyLarge())
                        .foregroundColor(selected ? palette.onPrimaryContainer : palette.text)
                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(NeoFont.bodySmall())
                            .foregroundColor(palette.secondaryText)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(selected ? palette.primaryContainer : palette.surfaceVariant)
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(selected ? palette.primary : palette.outlineVariant, lineWidth: selected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityIdentifier(tag)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : [.isButton])
    }

    private var stepDots: some View {
        HStack(spacing: 6) {
            ForEach(0..<welcomePageCount, id: \.self) { index in
                Capsule()
                    .fill(index == page ? palette.primary : palette.outlineVariant)
                    .frame(width: index == page ? 22 : 8, height: 8)
            }
        }
    }
}

struct NeoBottomSheet<Content: View>: View {
    let onDismiss: () -> Void
    @ViewBuilder var content: Content
    @Environment(\.palette) private var palette

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
                .accessibilityIdentifier("sheet_scrim")
            VStack(spacing: 0) {
                Capsule()
                    .fill(palette.outline)
                    .frame(width: 32, height: 4)
                    .padding(.top, 10)
                    .padding(.bottom, 2)
                content
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: UIScreen.main.bounds.height * 0.92, alignment: .top)
            .background(palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
        .ignoresSafeArea(edges: .bottom)
    }
}

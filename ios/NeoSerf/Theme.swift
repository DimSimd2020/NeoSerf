import SwiftUI

enum AppTheme: String, CaseIterable, Identifiable, Codable {
    case system
    case light
    case dark

    var id: String { rawValue }

    /// Same titles as Android `AppTheme.title`.
    var title: String {
        switch self {
        case .system: return "Как на устройстве"
        case .light: return "Светлая"
        case .dark: return "Тёмная"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    var icon: String {
        switch self {
        case .system: return NeoIcons.device
        case .light: return NeoIcons.wbSunny
        case .dark: return NeoIcons.nightsStay
        }
    }

    static var androidName: [AppTheme: String] {
        [.system: "SYSTEM", .light: "LIGHT", .dark: "DARK"]
    }

    var testTag: String { "settings_theme_\(AppTheme.androidName[self] ?? rawValue.uppercased())" }
    var welcomeTag: String { "welcome_theme_\(AppTheme.androidName[self] ?? rawValue.uppercased())" }
}

enum NeoFont {
    static let spaceGrotesk = "SpaceGroteskLight-Regular"
    static let inter = "Inter-Regular"
    static let mono = "JetBrainsMono-Regular"

    static func headlineLarge() -> Font { .custom(spaceGrotesk, size: 28).weight(.bold) }
    static func headlineMedium() -> Font { .custom(spaceGrotesk, size: 22).weight(.bold) }
    static func headlineSmall() -> Font { .custom(spaceGrotesk, size: 20).weight(.semibold) }
    static func titleLarge() -> Font { .custom(spaceGrotesk, size: 18).weight(.semibold) }
    static func titleMedium() -> Font { .custom(inter, size: 16).weight(.semibold) }
    static func bodyLarge() -> Font { .custom(inter, size: 15) }
    static func bodyMedium() -> Font { .custom(inter, size: 14) }
    static func bodySmall() -> Font { .custom(inter, size: 12) }
    static func labelLarge() -> Font { .custom(inter, size: 13).weight(.medium) }
    static func labelSmall() -> Font { .custom(mono, size: 11) }
}

enum NeoColor {
    static let darkBackground = Color(red: 0x19 / 255, green: 0x19 / 255, blue: 0x19 / 255)
    static let darkSurface = Color(red: 0x19 / 255, green: 0x19 / 255, blue: 0x19 / 255)
    static let darkSurfaceVariant = Color(red: 0x25 / 255, green: 0x25 / 255, blue: 0x25 / 255)
    static let darkSurfaceElevated = Color(red: 0x30 / 255, green: 0x30 / 255, blue: 0x30 / 255)
    static let darkOutline = Color(red: 0x35 / 255, green: 0x35 / 255, blue: 0x35 / 255)
    static let darkText = Color(red: 0xED / 255, green: 0xED / 255, blue: 0xED / 255)
    static let darkSecondary = Color(red: 0xA3 / 255, green: 0xA3 / 255, blue: 0xA3 / 255)
    static let darkPrimary = Color(red: 0x45 / 255, green: 0x9F / 255, blue: 1.0)
    static let darkOnPrimary = Color(red: 0x07 / 255, green: 0x13 / 255, blue: 0x1E / 255)
    static let darkPrimaryContainer = Color(red: 0x1D / 255, green: 0x30 / 255, blue: 0x45 / 255)
    static let darkOnPrimaryContainer = Color(red: 0xC7 / 255, green: 0xE4 / 255, blue: 1.0)
    static let darkTertiary = Color(red: 0x7B / 255, green: 0x61 / 255, blue: 1.0)
    static let darkTertiaryContainer = Color(red: 0x27 / 255, green: 0x1E / 255, blue: 0x54 / 255)
    static let darkOnTertiaryContainer = Color(red: 0xE0 / 255, green: 0xD9 / 255, blue: 1.0)
    static let darkError = Color(red: 0xEF / 255, green: 0x64 / 255, blue: 0x61 / 255)

    static let lightBackground = Color(red: 0xF3 / 255, green: 0xF3 / 255, blue: 0xF3 / 255)
    static let lightSurfaceVariant = Color(red: 0xE6 / 255, green: 0xE6 / 255, blue: 0xE6 / 255)
    static let lightOutline = Color(red: 0xCB / 255, green: 0xCB / 255, blue: 0xCB / 255)
    static let lightOutlineVariant = Color(red: 0xDA / 255, green: 0xDA / 255, blue: 0xDA / 255)
    static let lightText = Color(red: 0x1B / 255, green: 0x1B / 255, blue: 0x1B / 255)
    static let lightSecondary = Color(red: 0x60 / 255, green: 0x60 / 255, blue: 0x60 / 255)
    static let lightPrimary = Color(red: 0x16 / 255, green: 0x6A / 255, blue: 0xD8 / 255)
    static let lightPrimaryContainer = Color(red: 0xD9 / 255, green: 0xE9 / 255, blue: 1.0)
    static let lightOnPrimaryContainer = Color(red: 0x12 / 255, green: 0x38 / 255, blue: 0x5E / 255)
    static let lightTertiary = Color(red: 0x65 / 255, green: 0x45 / 255, blue: 0xCC / 255)
    static let lightTertiaryContainer = Color(red: 0xE8 / 255, green: 0xDE / 255, blue: 1.0)
    static let lightOnTertiaryContainer = Color(red: 0x30 / 255, green: 0x21 / 255, blue: 0x5C / 255)
    static let lightError = Color(red: 0xB3 / 255, green: 0x26 / 255, blue: 0x1E / 255)
    static let lightSurfaceHighest = Color(red: 0xDD / 255, green: 0xDD / 255, blue: 0xDD / 255)
}

struct Palette {
    let background: Color
    let surface: Color
    let surfaceVariant: Color
    let surfaceContainerHighest: Color
    let outline: Color
    let outlineVariant: Color
    let text: Color
    let secondaryText: Color
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let tertiary: Color
    let tertiaryContainer: Color
    let error: Color
    let isDark: Bool

    static func current(colorScheme: ColorScheme) -> Palette {
        if colorScheme == .dark {
            return Palette(
                background: NeoColor.darkBackground,
                surface: NeoColor.darkSurface,
                surfaceVariant: NeoColor.darkSurfaceVariant,
                surfaceContainerHighest: NeoColor.darkSurfaceElevated,
                outline: NeoColor.darkOutline,
                outlineVariant: NeoColor.darkOutline,
                text: NeoColor.darkText,
                secondaryText: NeoColor.darkSecondary,
                primary: NeoColor.darkPrimary,
                onPrimary: NeoColor.darkOnPrimary,
                primaryContainer: NeoColor.darkPrimaryContainer,
                onPrimaryContainer: NeoColor.darkOnPrimaryContainer,
                tertiary: NeoColor.darkTertiary,
                tertiaryContainer: NeoColor.darkTertiaryContainer,
                error: NeoColor.darkError,
                isDark: true
            )
        }
        return Palette(
            background: NeoColor.lightBackground,
            surface: NeoColor.lightBackground,
            surfaceVariant: NeoColor.lightSurfaceVariant,
            surfaceContainerHighest: NeoColor.lightSurfaceHighest,
            outline: NeoColor.lightOutline,
            outlineVariant: NeoColor.lightOutlineVariant,
            text: NeoColor.lightText,
            secondaryText: NeoColor.lightSecondary,
            primary: NeoColor.lightPrimary,
            onPrimary: .white,
            primaryContainer: NeoColor.lightPrimaryContainer,
            onPrimaryContainer: NeoColor.lightOnPrimaryContainer,
            tertiary: NeoColor.lightTertiary,
            tertiaryContainer: NeoColor.lightTertiaryContainer,
            error: NeoColor.lightError,
            isDark: false
        )
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.current(colorScheme: .dark)
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

struct PaletteInjector: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.environment(\.palette, Palette.current(colorScheme: colorScheme))
    }
}

/// Matches Android `neoSwitchColors()`: the unchecked thumb stays visible on a dark surface.
struct NeoSwitch: View {
    @Binding var isOn: Bool
    var onChange: ((Bool) -> Void)? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        Toggle("", isOn: Binding(
            get: { isOn },
            set: { next in
                isOn = next
                onChange?(next)
            }
        ))
        .labelsHidden()
        .toggleStyle(SwitchToggleStyle(tint: palette.primary))
    }
}

struct NeoPrimaryButton: View {
    let title: String
    var fill: Color? = nil
    var foreground: Color? = nil
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(NeoFont.bodyLarge().weight(.semibold))
                .foregroundColor(foreground ?? palette.onPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(fill ?? palette.primary)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PlainButtonStyle())
    }
}

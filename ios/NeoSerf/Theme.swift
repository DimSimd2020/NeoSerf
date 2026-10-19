import SwiftUI

enum AppTheme: String, CaseIterable, Identifiable, Codable {
    case system
    case light
    case dark

    var id: String { rawValue }

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
        case .system: return "iphone"
        case .light: return "sun.max"
        case .dark: return "moon"
        }
    }
}

enum NeoColor {
    static let darkBackground = Color(red: 0x19 / 255, green: 0x19 / 255, blue: 0x19 / 255)
    static let darkSurface = Color(red: 0x19 / 255, green: 0x19 / 255, blue: 0x19 / 255)
    static let darkSurfaceVariant = Color(red: 0x25 / 255, green: 0x25 / 255, blue: 0x25 / 255)
    static let darkOutline = Color(red: 0x35 / 255, green: 0x35 / 255, blue: 0x35 / 255)
    static let darkText = Color(red: 0xED / 255, green: 0xED / 255, blue: 0xED / 255)
    static let darkSecondary = Color(red: 0xA3 / 255, green: 0xA3 / 255, blue: 0xA3 / 255)
    static let darkPrimary = Color(red: 0x45 / 255, green: 0x9F / 255, blue: 1.0)
    static let darkPrimaryContainer = Color(red: 0x1D / 255, green: 0x30 / 255, blue: 0x45 / 255)
    static let darkOnPrimaryContainer = Color(red: 0xC7 / 255, green: 0xE4 / 255, blue: 1.0)
    static let darkTertiary = Color(red: 0x7B / 255, green: 0x61 / 255, blue: 1.0)

    static let lightBackground = Color(red: 0xF3 / 255, green: 0xF3 / 255, blue: 0xF3 / 255)
    static let lightSurfaceVariant = Color(red: 0xE6 / 255, green: 0xE6 / 255, blue: 0xE6 / 255)
    static let lightOutline = Color(red: 0xCB / 255, green: 0xCB / 255, blue: 0xCB / 255)
    static let lightText = Color(red: 0x1B / 255, green: 0x1B / 255, blue: 0x1B / 255)
    static let lightSecondary = Color(red: 0x60 / 255, green: 0x60 / 255, blue: 0x60 / 255)
    static let lightPrimary = Color(red: 0x16 / 255, green: 0x6A / 255, blue: 0xD8 / 255)
    static let lightPrimaryContainer = Color(red: 0xD9 / 255, green: 0xE9 / 255, blue: 1.0)
    static let lightOnPrimaryContainer = Color(red: 0x12 / 255, green: 0x38 / 255, blue: 0x5E / 255)
    static let lightTertiary = Color(red: 0x65 / 255, green: 0x45 / 255, blue: 0xCC / 255)
}

struct Palette {
    let background: Color
    let surface: Color
    let surfaceVariant: Color
    let outline: Color
    let text: Color
    let secondaryText: Color
    let primary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let tertiary: Color
    let error: Color

    static func current(colorScheme: ColorScheme) -> Palette {
        if colorScheme == .dark {
            return Palette(
                background: NeoColor.darkBackground,
                surface: NeoColor.darkSurface,
                surfaceVariant: NeoColor.darkSurfaceVariant,
                outline: NeoColor.darkOutline,
                text: NeoColor.darkText,
                secondaryText: NeoColor.darkSecondary,
                primary: NeoColor.darkPrimary,
                primaryContainer: NeoColor.darkPrimaryContainer,
                onPrimaryContainer: NeoColor.darkOnPrimaryContainer,
                tertiary: NeoColor.darkTertiary,
                error: Color(red: 0xEF / 255, green: 0x64 / 255, blue: 0x61 / 255)
            )
        }
        return Palette(
            background: NeoColor.lightBackground,
            surface: NeoColor.lightBackground,
            surfaceVariant: NeoColor.lightSurfaceVariant,
            outline: NeoColor.lightOutline,
            text: NeoColor.lightText,
            secondaryText: NeoColor.lightSecondary,
            primary: NeoColor.lightPrimary,
            primaryContainer: NeoColor.lightPrimaryContainer,
            onPrimaryContainer: NeoColor.lightOnPrimaryContainer,
            tertiary: NeoColor.lightTertiary,
            error: Color(red: 0xB3 / 255, green: 0x26 / 255, blue: 0x1E / 255)
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

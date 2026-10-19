import SwiftUI

/// Shared NeoSerf icons. Asset catalog PDFs are generated from `desktop/assets/icons/*.svg`.
enum NeoIcons {
    static let arrowBack = "icon-back"
    static let arrowForward = "icon-forward"
    static let home = "icon-home"
    static let refresh = "icon-reload"
    static let moreVert = "icon-menu"
    static let add = "icon-plus"
    static let close = "icon-close"
    static let bookmarkBorder = "icon-bookmark"
    static let bookmark = "icon-bookmark-filled"
    static let search = "icon-search"
    static let history = "icon-history"
    static let key = "icon-key"
    static let lock = "icon-lock"
    static let shield = "icon-shield"
    static let security = "icon-shield-off"
    static let tune = "icon-settings"
    static let visibility = "icon-eye"
    static let visibilityOff = "icon-eye-off"
    static let myLocation = "icon-location"
    static let logout = "icon-logout"
    static let password = "icon-password"
    static let delete = "icon-delete"
    static let contentCopy = "icon-copy"
    static let autoFixHigh = "icon-autofill"
    static let publicIcon = "icon-globe"
    static let cloudSync = "icon-cloud-sync"
    static let textDecrease = "icon-text-minus"
    static let textIncrease = "icon-text-plus"
    static let wbSunny = "icon-sun"
    static let nightsStay = "icon-moon"
    static let cloud = "icon-cloud"
    static let blurOn = "icon-fog"
    static let acUnit = "icon-snow"
    static let thunderstorm = "icon-storm"
    static let waterDrop = "icon-drop"
    static let device = "icon-device"
}

struct NeoIcon: View {
    let name: String
    var size: CGFloat = 20
    var weight: Font.Weight = .regular

    var body: some View {
        Image(name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

struct FinLogo: View {
    var size: CGFloat = 96

    var body: some View {
        Image("Logo")
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

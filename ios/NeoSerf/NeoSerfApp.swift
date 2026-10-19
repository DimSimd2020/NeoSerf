import SwiftUI

@main
struct NeoSerfApp: App {
    init() {
        ContentBlockerStore.shared.prepare()
        if let appearance = UITestLaunch.appearance {
            // Force the system trait collection for screenshot runs before first layout.
            if appearance == "dark" {
                UserDefaults.standard.set("Dark", forKey: "AppleInterfaceStyle")
            } else {
                UserDefaults.standard.set("Light", forKey: "AppleInterfaceStyle")
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            BrowserView()
        }
    }
}

import SwiftUI

@main
struct NeoSerfApp: App {
    init() {
        ContentBlockerStore.shared.prepare()
    }

    var body: some Scene {
        WindowGroup {
            BrowserView()
        }
    }
}

import SwiftUI

/// The whole native shell: one SwiftUI scene that hosts the Compose Multiplatform UI.
/// Everything the app does lives in Kotlin (`composeApp/src/iosMain` + `commonMain`); this file only gives it a window.
@main
struct ClavisApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all) // Compose lays out against the safe area itself
        }
    }
}

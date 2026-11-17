import SwiftUI
import ComposeApp // the static framework linked from :composeApp (Kotlin `MainViewController()`)

/// Hosts the `UIViewController` that `ComposeUIViewController` returns.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

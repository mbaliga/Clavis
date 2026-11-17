package io.github.mbaliga.clavis

import androidx.compose.ui.window.ComposeUIViewController
import io.github.mbaliga.clavis.platform.iosServices
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.store.ClavisStore
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIApplicationWillTerminateNotification
import platform.UIKit.UIViewController

/**
 * The one entry point the SwiftUI shell (`iosApp/`) calls. Builds the store from the iOS services, and writes the
 * learner document whenever the app is about to lose the foreground (the analogue of Android's `onStop`): the process
 * can be suspended and killed after that with no further callback.
 */
@OptIn(ExperimentalResourceApi::class)
fun MainViewController(): UIViewController {
    val store = ClavisStore(iosServices(), readResource = { path -> Res.readBytes(path) })
    val center = NSNotificationCenter.defaultCenter
    for (name in listOf(UIApplicationWillResignActiveNotification, UIApplicationDidEnterBackgroundNotification, UIApplicationWillTerminateNotification)) {
        center.addObserverForName(name, `object` = null, queue = NSOperationQueue.mainQueue) { _ -> store.flushSaves() }
    }
    return ComposeUIViewController { ClavisApp(store) }
}

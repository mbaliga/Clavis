// Ubuntu Touch shell for the FROZEN ten-language Clavis reference build (docs/PORTING.md 6.2, D-31 Option A).
// This is a web page in a WebView, not the native Clavis app. All logic lives in www/index.html.
// Evidence: CODE-ONLY. qmllint-clean as syntax; Morph.Web is not installable off a Lomiri device, so nothing
// here has been loaded by a real Morph WebView (see docs/DEVICE_CHECKLIST_UT.md).
import QtQuick 2.12
import QtQuick.Window 2.12
import Morph.Web 0.1
import QtWebEngine 1.7

Window {
    id: root
    visible: true
    width: 400
    height: 800
    title: "Clavis (reference build)"

    WebView {
        id: web
        anchors.fill: parent
        url: Qt.resolvedUrl("../www/index.html")

        // The page makes no request of its own. Anything that is not the packaged file is refused, so a stray
        // link can never turn into network traffic (the click holds no `networking` policy group either).
        onNavigationRequested: {
            if (request.url.toString().indexOf("file://") !== 0
                    && request.url.toString().indexOf("blob:") !== 0
                    && request.url.toString().indexOf("data:") !== 0) {
                request.action = WebEngineNavigationRequest.IgnoreRequest
            }
        }
        onNewViewRequested: {
            // target=_blank citation links are not opened from this build (no url_dispatcher policy requested).
        }
    }
}

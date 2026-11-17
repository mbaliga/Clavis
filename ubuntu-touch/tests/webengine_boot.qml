// Desktop QtWebEngine 5.15 stand-in for the click's WebView: loads the staged page and reports. NOT a Morph/Lomiri
// test. @PAGE@ is substituted by run_webengine_boot.sh.
import QtQuick 2.12
import QtQuick.Window 2.12
import QtWebEngine 1.7
Window {
    visible: true; width: 400; height: 800
    WebEngineView {
        id: web; anchors.fill: parent
        url: "file://@PAGE@"
        onJavaScriptConsoleMessage: console.log("JSCONSOLE", level, message, sourceID, lineNumber)
        onLoadingChanged: if (loadRequest.status === WebEngineLoadRequest.LoadSucceededStatus) settle.start()
    }
    Timer { id: settle; interval: 6000; onTriggered: web.runJavaScript(
        "JSON.stringify({text:document.body.innerText.slice(0,160),nodes:document.querySelectorAll('*').length," +
        "localStorage:(localStorage.setItem('k','v'),localStorage.getItem('k')),title:document.title})",
        function (r) { console.log("RESULT", r); Qt.quit() }) }
    Timer { interval: 80000; running: true; onTriggered: { console.log("TIMEOUT"); Qt.quit() } }
}

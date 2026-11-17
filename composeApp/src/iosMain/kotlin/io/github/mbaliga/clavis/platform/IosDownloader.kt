package io.github.mbaliga.clavis.platform

import io.github.mbaliga.clavis.core.ClavisVersion
import io.github.mbaliga.clavis.core.Downloader
import io.github.mbaliga.clavis.core.DownloadPolicy
import io.github.mbaliga.clavis.core.state.FileIo
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import io.github.mbaliga.clavis.platform.toByteArray
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask

import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * D-13 on iOS: the only network path in the app, run solely when the learner taps a download. `NSURLSession` with an
 * ephemeral configuration (no cache, cookies or credential storage) and App Transport Security left at its defaults.
 * The guards are the shared [DownloadPolicy] (the same rules the JVM downloaders implement by hand, unit-tested on
 * the JVM): https only for the request and every redirect target, at most 5 redirects, at most 25 MB (declared and
 * running), 2xx only. The body is held in memory (bounded by the cap), written to `<toPath>.part`, then renamed over
 * the target, so a half-downloaded clip is never mistaken for a cached one. Returns false on any failure.
 *
 * NEEDS-DEVICE-VALIDATION: compiled only by the macOS CI lane, never run.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, UnsafeNumber::class)
class IosDownloader : Downloader {
    override suspend fun download(url: String, toPath: String, onProgress: (Long, Long?) -> Unit): Boolean {
        if (!DownloadPolicy.isHttps(url)) return false
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val ok = suspendCancellableCoroutine<NSData?> { cont ->
            val delegate = Delegate(onProgress) { body -> if (cont.isActive) cont.resume(body) }
            val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
            config.timeoutIntervalForRequest = 30.0
            val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }
            val session = NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = queue)
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            request.setValue(
                "Clavis/${ClavisVersion.NAME} (offline-first language app; user-initiated download)",
                forHTTPHeaderField = "User-Agent",
            )
            val task = session.dataTaskWithRequest(request)
            cont.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
            task.resume()
            session.finishTasksAndInvalidate()
        } ?: return false
        return try {
            FileIo.mkdirs(toPath.substringBeforeLast('/', ""))
            val part = "$toPath.part"
            FileIo.write(part, ok.toByteArray())
            FileIo.rename(part, toPath)
            true
        } catch (e: Exception) {
            FileIo.delete("$toPath.part")
            false
        }
    }

    /** Collects the body under the policy; calls [done] exactly once with the bytes, or null on any refusal or error. */
    private class Delegate(
        val onProgress: (Long, Long?) -> Unit,
        val done: (NSData?) -> Unit,
    ) : NSObject(), NSURLSessionDataDelegateProtocol {
        private val redirects = DownloadPolicy.RedirectGuard()
        private val budget = DownloadPolicy.ByteBudget()
        private val body = platform.Foundation.NSMutableData()
        private var total: Long? = null
        private var finished = false

        private fun finish(result: NSData?) {
            if (finished) return
            finished = true
            done(result)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            val target = newRequest.URL?.absoluteString
            if (target != null && redirects.allow(target)) {
                completionHandler(newRequest)
            } else {
                completionHandler(null) // refuse: the 3xx response is delivered and then rejected below
                finish(null)
            }
        }

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveResponse: NSURLResponse,
            completionHandler: (NSURLSessionResponseDisposition) -> Unit,
        ) {
            val http = didReceiveResponse as? NSHTTPURLResponse
            val declared = didReceiveResponse.expectedContentLength
            val acceptable = http != null &&
                DownloadPolicy.isSuccess(http.statusCode.toInt()) &&
                didReceiveResponse.URL?.absoluteString?.let { DownloadPolicy.isHttps(it) } == true &&
                DownloadPolicy.declaredLengthAllowed(declared)
            if (acceptable) {
                total = declared.takeIf { it > 0 }
                completionHandler(NSURLSessionResponseAllow)
            } else {
                completionHandler(NSURLSessionResponseCancel)
                finish(null)
            }
        }

        override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
            if (finished) return
            if (!budget.add(didReceiveData.length.toInt())) {
                dataTask.cancel()
                finish(null)
                return
            }
            body.appendData(didReceiveData)
            onProgress(budget.received, total)
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            finish(if (didCompleteWithError == null && !finished) body else null)
        }
    }
}

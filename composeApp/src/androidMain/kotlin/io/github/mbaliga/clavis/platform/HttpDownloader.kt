package io.github.mbaliga.clavis.platform

import io.github.mbaliga.clavis.core.ClavisVersion
import io.github.mbaliga.clavis.core.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * D-13: the only network path in the app, and it runs solely when the learner taps a download
 * (a Commons or Tatoeba recording). Plain `HttpURLConnection` with a descriptive User-Agent
 * (Wikimedia's policy) and these guards:
 *
 * - `https://` only — the request URL and every redirect target; anything else is refused;
 * - at most [MAX_REDIRECTS] redirects, followed by hand so the scheme check applies to each hop;
 * - at most [MAX_BYTES], checked against `Content-Length` up front and against the bytes read;
 * - the body streams into `<toPath>.part`, renamed over the target only on success and deleted on
 *   any failure, so a half-downloaded clip is never mistaken for a cached one.
 *
 * Returns false on any failure; the caller names the file (a SHA-256 of the URL), never the server.
 */
class HttpDownloader : Downloader {
    companion object {
        const val MAX_BYTES: Long = 25L * 1024 * 1024
        const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "Clavis/${ClavisVersion.NAME} (offline-first language app; user-initiated download)"

        internal fun isHttps(u: URL): Boolean = u.protocol.equals("https", ignoreCase = true)
    }

    override suspend fun download(url: String, toPath: String, onProgress: (Long, Long?) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val target = File(toPath)
        val tmp = File("$toPath.part")
        var ok = false
        try {
            val conn = connect(URL(url)) ?: return@withContext false
            val total = conn.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > MAX_BYTES) return@withContext false
            target.parentFile?.mkdirs()
            var done = 0L
            conn.inputStream.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > MAX_BYTES) return@withContext false
                        out.write(buf, 0, n)
                        onProgress(done, total)
                    }
                }
            }
            if (target.exists()) target.delete()
            ok = tmp.renameTo(target) || tmp.copyTo(target, overwrite = true).exists()
            ok
        } catch (e: Exception) {
            false
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Opens [start], following up to [MAX_REDIRECTS] redirects; null when a hop is not https or the status is not 2xx. */
    private fun connect(start: URL): HttpURLConnection? {
        var current = start
        var hops = 0
        while (true) {
            if (!isHttps(current)) return null
            val c = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location == null || ++hops > MAX_REDIRECTS) return null
                current = URL(current, location)
                continue
            }
            if (code !in 200..299) { c.disconnect(); return null }
            return c
        }
    }
}

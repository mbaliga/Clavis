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
 * (a Commons or Tatoeba recording). Plain `HttpURLConnection`, redirects followed, a descriptive
 * User-Agent (Wikimedia's policy), atomic `.part` → final rename; returns false on any failure.
 */
class HttpDownloader : Downloader {
    override suspend fun download(url: String, toPath: String, onProgress: (Long, Long?) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Clavis/${ClavisVersion.NAME} (offline-first language app; user-initiated download)")
            }
            if (conn.responseCode !in 200..299) return@runCatching false
            val total = conn.contentLengthLong.takeIf { it > 0 }
            val target = File(toPath)
            target.parentFile?.mkdirs()
            val tmp = File("$toPath.part")
            var done = 0L
            conn.inputStream.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target) || tmp.copyTo(target, overwrite = true).exists().also { tmp.delete() }
        }.getOrDefault(false)
    }
}

package io.github.mbaliga.clavis.core

/**
 * The D-13 download guards as pure functions, so a platform downloader that cannot share the JVM
 * `HttpURLConnection` code (iOS: `NSURLSession` with a delegate) enforces exactly the same rules and the rules
 * themselves are unit-tested on the JVM. The JVM downloaders keep their own copies (byte-identical in
 * `androidMain` and `desktopMain`); this object is the specification the iOS one is written against.
 *
 * - `https://` only, for the request URL and every redirect target;
 * - at most [MAX_REDIRECTS] redirects;
 * - at most [MAX_BYTES], checked against the declared length up front and against the running count;
 * - only 2xx responses are stored.
 */
object DownloadPolicy {
    const val MAX_BYTES: Long = 25L * 1024 * 1024
    const val MAX_REDIRECTS = 5

    /** True only for an `https://` URL that names a host. */
    fun isHttps(url: String): Boolean {
        val scheme = "https://"
        if (url.length <= scheme.length || !url.startsWith(scheme, ignoreCase = true)) return false
        val c = url[scheme.length]
        return c != '/' && c != '?' && c != '#' && c != ' '
    }

    fun isSuccess(status: Int): Boolean = status in 200..299

    /** A declared length that is unknown (<= 0) passes here and is then bounded by the running count. */
    fun declaredLengthAllowed(declared: Long): Boolean = declared <= MAX_BYTES

    /** Counts redirects and vets each target. */
    class RedirectGuard {
        var hops: Int = 0
            private set

        /** Records one redirect to [target]; false (refuse) when the target is not https or this is hop [MAX_REDIRECTS]+1. */
        fun allow(target: String): Boolean {
            if (!isHttps(target)) return false
            hops++
            return hops <= MAX_REDIRECTS
        }
    }

    /** Running byte budget for a body whose declared length may be absent or wrong. */
    class ByteBudget(private val limit: Long = MAX_BYTES) {
        var received: Long = 0
            private set

        /** Adds [n] bytes; false when the total now exceeds the limit (the caller aborts and deletes the partial file). */
        fun add(n: Int): Boolean {
            received += n
            return received <= limit
        }
    }
}

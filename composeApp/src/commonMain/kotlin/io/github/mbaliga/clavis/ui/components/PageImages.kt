package io.github.mbaliga.clavis.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Decoded page WebPs, least-recently-used, capped at [capacity] (≈12 pages × 640×904 ≈ 28 MB).
 * The bytes come from the comics pack (`pageBytes(pageKey)`); decoding runs off the UI thread.
 */
class PageImages(private val bytesFor: suspend (String) -> ByteArray, private val capacity: Int = 12) {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val order = ArrayDeque<String>()
    private val mutex = Mutex()

    suspend fun get(key: String): ImageBitmap? {
        mutex.withLock { cache[key] }?.let { touch(key); return it }
        val bmp = try {
            withContext(Dispatchers.Default) { bytesFor(key).decodeToImageBitmap() }
        } catch (e: Exception) {
            null
        } ?: return null
        mutex.withLock {
            cache[key] = bmp
            order.remove(key); order.addLast(key)
            while (order.size > capacity) { val old = order.removeFirst(); cache.remove(old) }
        }
        return bmp
    }

    private suspend fun touch(key: String) = mutex.withLock { order.remove(key); order.addLast(key) }

    fun peek(key: String): ImageBitmap? = cache[key]
    val size: Int get() = cache.size
}

/** The page bitmap for [key], null while loading (or if the page is missing from the pack). */
@Composable
fun rememberPageBitmap(images: PageImages, key: String): State<ImageBitmap?> =
    produceState<ImageBitmap?>(images.peek(key), key) { value = images.get(key) }

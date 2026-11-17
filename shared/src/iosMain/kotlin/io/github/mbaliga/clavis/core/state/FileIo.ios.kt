package io.github.mbaliga.clavis.core.state

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fsync
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.stat

/**
 * POSIX-only file primitives (no Foundation), so the same source compiles for Linux Native and was run there
 * as a stand-in for iOS. Write = `fopen` + `fwrite` + `fflush` + `fsync` + `fclose`; replace = `rename(2)`,
 * which is atomic on the same volume (D-12).
 */
@OptIn(ExperimentalForeignApi::class)
actual object FileIo {
    private const val S_IFMT_MASK = 0xF000
    private const val S_IFREG_BITS = 0x8000
    private const val S_IFDIR_BITS = 0x4000

    private fun modeOf(path: String): Int? = memScoped {
        val st = alloc<stat>()
        if (stat(path, st.ptr) != 0) null else st.st_mode.toInt() and S_IFMT_MASK
    }

    actual fun read(path: String): ByteArray? {
        if (modeOf(path) != S_IFREG_BITS) return null
        val fp = fopen(path, "rb") ?: return null
        try {
            var out = ByteArray(64 * 1024)
            var size = 0
            while (true) {
                if (size == out.size) out = out.copyOf(out.size * 2)
                val n = out.usePinned { pinned ->
                    fread(pinned.addressOf(size), 1.convert(), (out.size - size).convert(), fp).toInt()
                }
                if (n <= 0) break
                size += n
            }
            return out.copyOf(size)
        } finally {
            fclose(fp)
        }
    }

    actual fun write(path: String, bytes: ByteArray) {
        val slash = path.lastIndexOf('/')
        if (slash > 0) mkdirs(path.substring(0, slash))
        val fp = fopen(path, "wb") ?: throw IllegalStateException("cannot open $path for writing")
        try {
            if (bytes.isNotEmpty()) {
                val written = bytes.usePinned { fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), fp).toLong() }
                if (written != bytes.size.toLong()) throw IllegalStateException("short write to $path")
            }
            if (fflush(fp) != 0) throw IllegalStateException("flush failed for $path")
            fsync(fileno(fp)) // best effort, like the JVM actual
        } finally {
            fclose(fp)
        }
    }

    actual fun rename(from: String, to: String) {
        if (platform.posix.rename(from, to) != 0) throw IllegalStateException("rename $from -> $to failed")
    }

    actual fun delete(path: String) {
        remove(path)
    }

    actual fun exists(path: String): Boolean = access(path, F_OK) == 0

    actual fun mkdirs(path: String) {
        if (path.isEmpty()) return
        var i = if (path.startsWith("/")) 1 else 0
        while (i <= path.length) {
            val j = path.indexOf('/', i).let { if (it < 0) path.length else it }
            if (j > i) {
                val sub = path.substring(0, j)
                if (modeOf(sub) != S_IFDIR_BITS) mkdir(sub, 0x1ED.convert()) // 0755
            }
            i = j + 1
        }
    }
}

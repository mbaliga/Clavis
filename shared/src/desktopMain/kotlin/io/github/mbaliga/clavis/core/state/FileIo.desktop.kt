package io.github.mbaliga.clavis.core.state

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

actual object FileIo {
    actual fun read(path: String): ByteArray? {
        val f = File(path)
        return if (f.isFile) f.readBytes() else null
    }

    actual fun write(path: String, bytes: ByteArray) {
        val f = File(path)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { out ->
            out.write(bytes)
            out.flush()
            try { out.fd.sync() } catch (_: IOException) { }
        }
    }

    actual fun rename(from: String, to: String) {
        try {
            Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            val target = File(to)
            target.delete()
            if (!File(from).renameTo(target)) throw IOException("rename $from -> $to failed", e)
        }
    }

    actual fun delete(path: String) { File(path).delete() }
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun mkdirs(path: String) { File(path).mkdirs() }
}

package io.github.mbaliga.clavis.core.state

/** Minimal file primitives the state store needs; both targets implement them over java.io/java.nio. */
expect object FileIo {
    /** Whole-file read, or null when the file does not exist. */
    fun read(path: String): ByteArray?
    /** Whole-file write (creates parent directories), fsync'd. */
    fun write(path: String, bytes: ByteArray)
    /** Atomic replace of [to] by [from] where the filesystem allows it. */
    fun rename(from: String, to: String)
    fun delete(path: String)
    fun exists(path: String): Boolean
    fun mkdirs(path: String)
}

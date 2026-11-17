package io.github.mbaliga.clavis.core

/**
 * The importer refusals (D-7) as one pure function over bytes already read, for platform pickers that hand over
 * a file rather than a stream (iOS copies the chosen document into the app's temporary directory first). Android's
 * `readBoundedText` applies the same limits to a stream; the messages are the shared [Copy] strings either way.
 */
object ImportPolicy {
    /** Refuses on a declared size over the cap, before the file is read. */
    fun checkDeclaredSize(declared: Long?) {
        if (declared != null && declared > ImportLimits.MAX_BYTES) throw ImportRefusedException(Copy.IMPORT_TOO_LARGE)
    }

    /** Refuses a wrong extension (when [allowed] is non-empty), an oversize body, or a body that is not mostly text. */
    fun decode(name: String, bytes: ByteArray, allowed: List<String>): ImportedFile {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (allowed.isNotEmpty() && ext !in allowed) throw ImportRefusedException(Copy.importWrongKind(allowed))
        if (bytes.size.toLong() > ImportLimits.MAX_BYTES) throw ImportRefusedException(Copy.IMPORT_TOO_LARGE)
        val text = bytes.decodeToString()
        if (!ImportLimits.isText(text)) throw ImportRefusedException(Copy.IMPORT_NOT_TEXT)
        return ImportedFile(name, text)
    }
}

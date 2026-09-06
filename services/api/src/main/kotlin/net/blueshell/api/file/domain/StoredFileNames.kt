package net.blueshell.api.file.domain

import java.util.Locale

/**
 * What a stored file is called, at each of the three points where the answer differs: the
 * address it is stored at, the name a browser is told to save it as, and the extension both
 * are derived from.
 *
 * Rules rather than filesystem work, so they hold whatever the bytes are kept on.
 */
object StoredFileNames {

    /**
     * Where bytes of a kind live: the kind's directory and the file's own name, which is the
     * key the store is addressed by and the path a `File` row carries.
     */
    fun keyOf(directory: String, filename: String): String = "$directory/$filename"

    /**
     * The name content-addressed bytes are stored under: their hash, keeping the uploaded
     * extension so that what is served can be typed from its name alone. An upload with no
     * extension is stored under the bare hash, which is still an address.
     */
    fun hashedName(sha256: String, originalName: String): String {
        val extension = extensionOf(originalName)
        return if (extension.isBlank()) sha256 else "$sha256.${extension.lowercase(Locale.getDefault())}"
    }

    /**
     * What a saved copy of a file is called. The record keeps the uploaded name for the audit
     * trail, but the bytes may since have been converted, so the stem stays the uploader's and
     * the extension follows the bytes — otherwise a browser writes WebP into a `.jpg`.
     */
    fun servedName(uploadedName: String, storedKey: String): String {
        val stored = extensionOf(storedKey)
        val uploaded = extensionOf(uploadedName)
        if (stored.isBlank() || stored.equals(uploaded, ignoreCase = true)) return uploadedName
        val stem = if (uploaded.isBlank()) uploadedName else uploadedName.substringBeforeLast(".$uploaded")
        return "$stem.$stored"
    }

    /**
     * The extension of a name, in the case it was written in and without its dot.
     *
     * Read off the last segment, because an uploader's browser may send a whole path and a
     * directory that contains a dot is not an extension. A name that ends in a dot has none.
     */
    fun extensionOf(name: String): String {
        val last = name.substringAfterLast('/')
        val dot = last.lastIndexOf('.')
        if (dot < 0 || dot == last.length - 1) return ""
        return last.substring(dot + 1)
    }
}

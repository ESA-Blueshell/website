package net.blueshell.api.file.domain

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * What a stored file is, typed from its name.
 *
 * The name is the answer here because a stored name is the hash plus the uploaded extension,
 * and converted bytes are stored under the extension they were converted to. Sniffing the
 * bytes would say the same thing at the price of opening them.
 *
 * The platform's own table is asked first, so a deployment that knows a type this one does not
 * still answers; [EXTENSIONS] is the floor under it, because a container image with no
 * `mime.types` would otherwise call every upload a stream of bytes.
 */
object MediaTypes {

    const val OCTET_STREAM = "application/octet-stream"

    /** The type of [filename], or [OCTET_STREAM] where nothing recognises its extension. */
    fun ofName(filename: String): String =
        probe(filename) ?: EXTENSIONS[StoredFileNames.extensionOf(filename).lowercase(Locale.getDefault())]
            ?: OCTET_STREAM

    // A name lookup, not a read: the platform table is keyed by extension and the file the
    // name would denote is never opened, so this holds for bytes that are not on a disk.
    private fun probe(filename: String): String? =
        runCatching { Files.probeContentType(Path.of(filename)) }.getOrNull()

    private val EXTENSIONS = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "html" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "json" to "application/json",
        "xml" to "application/xml",
        "zip" to "application/zip",
        "mp4" to "video/mp4",
        "mp3" to "audio/mpeg",
    )
}

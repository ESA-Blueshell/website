package net.blueshell.api.file.domain

import org.springframework.http.MediaTypeFactory
import java.util.Locale

/**
 * What a stored file is, typed from its name.
 *
 * The name is the answer here because a stored name is the hash plus the uploaded extension,
 * and converted bytes are stored under the extension they were converted to. Sniffing the bytes
 * would say the same thing at the price of opening them.
 *
 * [EXTENSIONS] is asked first, so the formats this site actually stores answer the same on every
 * deployment; Spring's own table fills in the rest. Neither builds a path — the name arrives
 * from an uploader, and a lookup that turned it into one would be reading the host filesystem
 * to answer a question about a string.
 */
object MediaTypes {

    const val OCTET_STREAM = "application/octet-stream"

    /** The type of [filename], or [OCTET_STREAM] where nothing recognises its extension. */
    fun ofName(filename: String): String {
        val extension = StoredFileNames.extensionOf(filename).lowercase(Locale.getDefault())
        return EXTENSIONS[extension]
            ?: MediaTypeFactory.getMediaType("file.$extension").map { it.toString() }.orElse(OCTET_STREAM)
    }

    private val EXTENSIONS = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "webp" to "image/webp",
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

package net.blueshell.api.file.domain

import java.io.InputStream

/** What an uploaded PDF has to be: a file that opens as one, whatever the upload claimed. */
object PdfUploads {
    private const val MEDIA_TYPE = "application/pdf"
    private val MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

    fun isDeclared(mediaType: String): Boolean = mediaType.substringBefore(';').trim().equals(MEDIA_TYPE, ignoreCase = true)

    /** Throws where [content] does not start as a PDF does. Closes what it is given. */
    fun enforce(content: InputStream) {
        val head = content.use { it.readNBytes(MAGIC.size) }
        if (!head.contentEquals(MAGIC)) throw InvalidFileException("The uploaded file is not a PDF")
    }
}

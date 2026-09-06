package net.blueshell.api.file.web

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.domain.FileNotFoundException
import net.blueshell.api.file.domain.MediaTypes
import net.blueshell.api.file.domain.StoredFileNames
import net.blueshell.api.file.persistence.File
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.AbstractResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Turns stored bytes into an answer.
 *
 * Here rather than in the service the other modules call: a `ResponseEntity` is a web type, and
 * publishing one from the module's front door would make every caller of `file` a caller of
 * Spring MVC. What the service answers is a record; what a request gets back is this.
 */
@Component
class FileResponses(
    private val uploads: BlobStore,
    @Qualifier("assetBlobStore") private val assets: BlobStore,
) {

    /**
     * A public file, sent to be rendered rather than saved.
     *
     * Inline where [attachment] attaches: this answers an image tag, and an attachment
     * disposition makes the browser download it instead of drawing it. Cached for a year
     * because the url is a content hash, so these bytes can never become the wrong ones.
     */
    fun publicFile(file: File): ResponseEntity<Resource> =
        answer(
            resource = uploads.resourceAt(file.path) { FileNotFoundException("name=${file.name}") },
            mediaType = file.mediaType,
            disposition = ContentDisposition.inline()
                .filename(StoredFileNames.servedName(file.name, file.path)).build(),
            cache = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable(),
        )

    /** A file sent to be saved, under the name it was uploaded as. */
    fun attachment(file: File): ResponseEntity<Resource> =
        answer(
            resource = uploads.resourceAt(file.path) { FileNotFoundException("name=${file.name}") },
            mediaType = file.mediaType,
            disposition = ContentDisposition.attachment().filename(file.name).build(),
            cache = CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic(),
        )

    /**
     * Something the release shipped, which has no record behind it — so its type is read off
     * its name rather than out of a column.
     */
    fun asset(filename: String): ResponseEntity<Resource> =
        answer(
            resource = assets.resourceAt(filename) { FileNotFoundException("asset=$filename") },
            mediaType = MediaTypes.ofName(filename),
            disposition = ContentDisposition.attachment().filename(filename).build(),
            cache = CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic(),
        )

    private fun answer(
        resource: Resource,
        mediaType: String,
        disposition: ContentDisposition,
        cache: CacheControl,
    ): ResponseEntity<Resource> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.valueOf(mediaType)
        headers.contentDisposition = disposition
        return ResponseEntity.ok().cacheControl(cache).headers(headers).body(resource)
    }

    private fun BlobStore.resourceAt(key: String, missing: () -> RuntimeException): Resource {
        if (!exists(key)) throw missing()
        return BlobResource(this, key)
    }
}

/** Stored bytes as something Spring can write to a response, opened once it starts writing. */
private class BlobResource(private val blobs: BlobStore, private val key: String) : AbstractResource() {

    override fun getDescription(): String = "blob [$key]"

    override fun getInputStream(): InputStream = blobs.open(key)

    override fun exists(): Boolean = blobs.exists(key)

    override fun contentLength(): Long = blobs.sizeOf(key) ?: super.contentLength()
}

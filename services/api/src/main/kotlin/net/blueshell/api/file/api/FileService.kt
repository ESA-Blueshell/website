package net.blueshell.api.file.api

import jakarta.annotation.PostConstruct
import net.blueshell.api.file.domain.FileDeleted
import net.blueshell.api.file.domain.EmptyFileException
import net.blueshell.api.file.domain.ImageDimensions
import net.blueshell.api.file.domain.FileNotFoundException
import net.blueshell.api.file.domain.FileStorageException
import net.blueshell.api.file.domain.FileTooLargeException
import net.blueshell.api.file.domain.PublicImageUploadPreparer
import net.blueshell.api.file.domain.UnsupportedMediaTypeException
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.shared.util.sanitizeForLog
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.io.OutputStream
import java.net.MalformedURLException
import java.nio.file.*
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@Service
class FileService @Autowired constructor(
    fileRepository: FileRepository,
    @Value($$"${storage.location}") storageLocation: String,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserService,
    private val eventBannerFiles: EventBannerFileLookup,
    private val publicImageUploads: PublicImageUploadPreparer,
) : BaseModelService<File, Long, FileRepository>(fileRepository) {
    private val rootLocation: Path = Paths.get(storageLocation)
    private val assetsLocation: Path = Paths.get("assets")

    @Transactional(readOnly = true)
    fun findByName(name: String): File {
        return repository.findByName(name).orElseThrow {
            FileNotFoundException("name=$name")
        }
    }

    @PostConstruct
    fun init() {
        try {
            Files.createDirectories(rootLocation)
        } catch (e: IOException) {
            throw RuntimeException(e.cause)
        }
    }

    /**
     * Store multipart file using content-hash path. Returns persisted File entity.
     */
    @Transactional
    fun storeMultipart(multipart: MultipartFile, type: FileType): File {
        if (multipart.isEmpty) {
            throw EmptyFileException()
        }
        enforce(type, multipart)

        val currentUserId = currentUserProvider.currentUser()?.id
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user")
        val uploader = users.findById(currentUserId)

        try {
            Files.createDirectories(rootLocation.resolve(type.directory))

            val originalName = multipart.originalFilename ?: "file"
            val source = Files.createTempFile(rootLocation, "upload-", ".tmp")
            var toStore: Path? = null

            try {
                multipart.inputStream.use { `in` ->
                    Files.newOutputStream(source, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                        .use { out ->
                            `in`.transferTo(out)
                        }
                }
            } catch (e: IOException) {
                Files.deleteIfExists(source)
                throw e
            }

            try {
                val preparedImage = publicImageUploads.prepare(source, type)
                toStore = preparedImage?.path ?: source
                val sha256 = sha256(toStore)
                val hashedFilename = if (preparedImage == null) {
                    buildHashedFilename(sha256, originalName)
                } else {
                    "$sha256.webp"
                }
                val path = type.directory + "/" + hashedFilename
                val fullPath = rootLocation.resolve(path).normalize()
                val mediaType = preparedImage?.mediaType
                    ?: resolveMediaType(hashedFilename, toStore, multipart.contentType ?: "")

                log.info("Storing {} at {}", sanitizeForLog(originalName), sanitizeForLog(fullPath))

                if (Files.exists(fullPath)) {
                    Files.deleteIfExists(toStore)
                } else {
                    try {
                        Files.move(toStore, fullPath, StandardCopyOption.ATOMIC_MOVE)
                    } catch (ignore: FileAlreadyExistsException) {
                        Files.deleteIfExists(toStore)
                    }
                }

                var entity = repository.findByPath(path).orElse(null)
                if (entity == null) {
                    entity = File(
                        name = originalName,
                        path = path,
                        uploader = uploader,
                        mediaType = mediaType,
                        size = null,
                        type = type,
                    )
                }

                populateAfterStore(
                    file = entity,
                    uploader = uploader,
                    name = originalName,
                    fullPath = fullPath,
                    path = path,
                    mediaType = mediaType,
                    width = preparedImage?.width,
                    height = preparedImage?.height,
                )
                entity.type = type

                return if (entity.id != null) {
                    update(entity)
                } else {
                    create(entity)
                }
            } finally {
                // Whatever did not become the stored file is a leftover: the upload itself once
                // a converted copy replaced it, and the converted copy when its address turned
                // out to be on disk already. Either may be the same path, and may already be
                // gone; deleting one that is not there is the answer, not a failure.
                Files.deleteIfExists(source)
                toStore?.let { Files.deleteIfExists(it) }
            }
        } catch (e: IOException) {
            throw FileStorageException("Failed to store file", e)
        } catch (e: NoSuchAlgorithmException) {
            throw FileStorageException("SHA-256 not available", e)
        }
    }

    /**
     * What a kind of file admits, checked before anything is written.
     *
     * The content type is the one the browser declared. It is a claim rather than a fact, and
     * this is a gate on what may be stored, not a guarantee about what was.
     */
    private fun enforce(type: FileType, multipart: MultipartFile) {
        val declared = multipart.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.getDefault())
        if (type.allowedMediaTypes.isNotEmpty() && declared !in type.allowedMediaTypes) {
            throw UnsupportedMediaTypeException(type, declared.ifBlank { "unknown" })
        }
        val max = type.maxBytes
        if (max != null && multipart.size > max) throw FileTooLargeException(type, max)
    }

    @Transactional
    override fun delete(entity: File) {
        super.delete(entity)
        trackedEvents.publish { actor ->
            FileDeleted(
                entity.id!!,
                entity.path,
                actor = actor
            )
        }
    }

    @Transactional
    override fun deleteById(id: Long) {
        val file = findById(id)
        delete(file)
    }

    private fun loadAsResource(file: File): Resource {
        try {
            val filePath = rootLocation.resolve(file.path)
            val resource: Resource = UrlResource(filePath.toUri())
            if (resource.exists() || resource.isReadable) return resource
            throw FileNotFoundException("name=${file.name}")
        } catch (_: MalformedURLException) {
            throw FileNotFoundException("name=${file.name}")
        }
    }

    private fun loadAssetAsResource(filename: String): Resource {
        try {
            val filePath = assetsLocation.resolve(filename)
            val resource: Resource = UrlResource(filePath.toUri())
            if (resource.exists() || resource.isReadable) return resource
            throw FileNotFoundException("asset=$filename")
        } catch (_: MalformedURLException) {
            throw FileNotFoundException("asset=$filename")
        }
    }

    /**
     * A file of a kind that exists to be drawn on a public page.
     *
     * A file of any other kind is reported missing rather than forbidden: whether one exists
     * is not something an anonymous caller has any business learning.
     */
    @Transactional(readOnly = true)
    fun findPubliclyReadable(path: String): File {
        val file = repository.findByPath(path).orElseThrow { FileNotFoundException("public path=") }
        if (!file.type.publiclyReadable) throw FileNotFoundException("public path=")
        return file
    }

    /**
     * A public file, sent to be rendered rather than saved.
     *
     * Inline where [prepareFileResponse] attaches: this answers an image tag, and an
     * attachment disposition makes the browser download it instead of drawing it.
     */
    @Transactional(readOnly = true)
    fun preparePublicFileResponse(file: File): ResponseEntity<Resource> {
        val resource = loadAsResource(file)
        val headers = HttpHeaders()
        headers.contentType = MediaType.valueOf(file.mediaType)
        headers.contentDisposition = ContentDisposition.inline().filename(servedFilename(file)).build()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .headers(headers)
            .body(resource)
    }

    @Transactional(readOnly = true)
    fun prepareFileResponse(file: File): ResponseEntity<Resource> {
        val resource = loadAsResource(file)
        val headers = HttpHeaders()
        headers.contentType = MediaType.valueOf(file.mediaType)
        headers.contentDisposition = ContentDisposition.attachment().filename(file.name).build()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
            .headers(headers)
            .body(resource)
    }

    fun prepareAssetResponse(filename: String): ResponseEntity<Resource> {
        val resource = loadAssetAsResource(filename)
        val headers = HttpHeaders()
        headers.contentType = MediaType.valueOf(detectContentType(filename, resource))
        headers.contentDisposition = ContentDisposition.attachment().filename(filename).build()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
            .headers(headers)
            .body(resource)
    }

    /**
     * What a saved copy of a file is called.
     *
     * The record keeps the name it was uploaded under, which is what an audit trail wants to
     * read. The bytes may since have been converted, though, and that name's extension then
     * describes something the file no longer is: a browser saving one would write WebP into a
     * file called `.jpg`. The stem stays the uploader's and the extension follows the bytes.
     */
    private fun servedFilename(file: File): String {
        val stored = getExtensionSafe(file.path)
        val uploaded = getExtensionSafe(file.name)
        if (stored.isBlank() || stored.equals(uploaded, ignoreCase = true)) return file.name
        val stem = if (uploaded.isBlank()) file.name else file.name.substringBeforeLast(".$uploaded")
        return "$stem.$stored"
    }

    private fun buildHashedFilename(sha256: String, originalName: String): String {
        val ext = getExtensionSafe(originalName)
        return if (ext.isBlank()) sha256 else (sha256 + "." + ext.lowercase(Locale.getDefault()))
    }

    private fun resolveMediaType(filename: String, path: Path, preferred: String?): String {
        if (!preferred.isNullOrBlank()) return preferred
        try {
            val probed = Files.probeContentType(path)
            return probed ?: detectContentType(filename, UrlResource(path.toUri()))
        } catch (_: Exception) {
            return "application/octet-stream"
        }
    }

    private fun populateAfterStore(
        file: File,
        uploader: User,
        name: String,
        fullPath: Path,
        path: String,
        mediaType: String,
        width: Int? = null,
        height: Int? = null,
    ) {
        file.name = name
        file.mediaType = mediaType
        file.uploader = uploader
        try {
            file.size = Files.size(fullPath)
        } catch (e: IOException) {
            throw RuntimeException("Could not read file size for: $path", e)
        }
        file.path = path
        if (width != null && height != null) {
            file.width = width
            file.height = height
            return
        }
        // Only a picture is opened for a size, which is the same net the backfill casts. A
        // size that cannot be read leaves whatever the record already had rather than clearing
        // it: the same content re-uploaded reuses its record, and a reader that answers this
        // time and not the next should not take a good answer away.
        if (ImageDimensions.mayHaveSize(mediaType)) {
            ImageDimensions.of(fullPath)?.let { size ->
                file.width = size.width
                file.height = size.height
            }
        }
    }

    private fun sha256(path: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            DigestInputStream(input, md).use { digest ->
                digest.transferTo(OutputStream.nullOutputStream())
            }
        }
        return HexFormat.of().formatHex(md.digest())
    }

    private fun detectContentType(filename: String, resource: Resource): String {
        try {
            var contentType = Files.probeContentType(Path.of(filename))
            if (contentType != null) return contentType
            resource.file
            contentType = Files.probeContentType(resource.file.toPath())
            if (contentType != null) return contentType
            return extToMime(getExtensionFromName(filename))
        } catch (_: Exception) {
            return "application/octet-stream"
        }
    }

    private fun getExtensionSafe(originalName: String): String {
        val name = Path.of(originalName).fileName.toString()
        val i = name.lastIndexOf('.')
        if (i < 0 || i == name.length - 1) return ""
        return name.substring(i + 1)
    }

    private fun getExtensionFromName(filename: String): String {
        val i = filename.lastIndexOf('.')
        if (i < 0 || i == filename.length - 1) return ""
        return filename.substring(i + 1).lowercase(Locale.getDefault())
    }

    private fun extToMime(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    fun findByBannerEventId(eventId: Long): File {
        val fileId = eventBannerFiles.fileIdForEvent(eventId)
            ?: throw FileNotFoundException("eventBanner eventId=$eventId")
        return repository.findById(fileId).orElseThrow {
            FileNotFoundException("eventBanner eventId=$eventId")
        }
    }

    fun deleteFromStoragePath(path: String) {
        val fullPath = rootLocation.resolve(path).normalize()

        try {
            if (Files.exists(fullPath)) {
                Files.deleteIfExists(fullPath)
            }
        } catch (e: IOException) {
            log.error("Failed to delete file {}", fullPath, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileService::class.java)
    }
}

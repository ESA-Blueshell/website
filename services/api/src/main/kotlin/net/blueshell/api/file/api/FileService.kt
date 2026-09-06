package net.blueshell.api.file.api

import net.blueshell.api.file.domain.ContentAddress
import net.blueshell.api.file.domain.EmptyFileException
import net.blueshell.api.file.domain.FileDeleted
import net.blueshell.api.file.domain.FileNotFoundException
import net.blueshell.api.file.domain.FileStorageException
import net.blueshell.api.file.domain.FileTooLargeException
import net.blueshell.api.file.domain.ImageDimensions
import net.blueshell.api.file.domain.ImageRenditionWriter
import net.blueshell.api.file.domain.MediaTypes
import net.blueshell.api.file.domain.PublicImageUploadPreparer
import net.blueshell.api.file.domain.ScratchFile
import net.blueshell.api.file.domain.ScratchSpace
import net.blueshell.api.file.domain.StoredFileNames
import net.blueshell.api.file.domain.UnsupportedMediaTypeException
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.shared.util.sanitizeForLog
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.io.InputStream
import java.util.Locale

@Service
class FileService @Autowired constructor(
    fileRepository: FileRepository,
    private val blobs: BlobStore,
    private val scratch: ScratchSpace,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserService,
    private val eventBannerFiles: EventBannerFileLookup,
    private val publicImageUploads: PublicImageUploadPreparer,
    private val imageRenditions: ImageRenditionWriter,
) : BaseModelService<File, Long, FileRepository>(fileRepository) {

    @Transactional(readOnly = true)
    fun findByName(name: String): File {
        return repository.findByName(name).orElseThrow {
            FileNotFoundException("name=$name")
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

        return store(
            content = multipart.inputStream,
            originalName = multipart.originalFilename ?: "file",
            declaredMediaType = multipart.contentType ?: "",
            type = type,
            uploader = uploader,
        )
    }

    /**
     * Stores bytes that arrived as something other than an upload, credited to [uploader].
     *
     * Shipped art is read off the classpath at start, with no request behind it, so the uploader
     * and the name are the caller's to say. Everything after is an upload's: the same conversion,
     * content address and widths. [content] is read once and closed here.
     */
    @Transactional
    fun store(
        content: InputStream,
        originalName: String,
        declaredMediaType: String,
        type: FileType,
        uploader: User,
    ): File {
        try {
            return scratch.hold(content).use { staged ->
                // A picture of a kind that is capped comes back converted; anything else is
                // stored as it was sent. The converted copy may be the staged bytes themselves,
                // which is why closing it is left to the block that owns them.
                val prepared = publicImageUploads.prepare(staged, type)
                val bytes = prepared?.bytes ?: staged
                try {
                    storeBytes(bytes, prepared, originalName, declaredMediaType, type, uploader)
                } finally {
                    bytes.close()
                }
            }
        } catch (e: IOException) {
            throw FileStorageException("Failed to store file", e)
        }
    }

    private fun storeBytes(
        bytes: ScratchFile,
        prepared: PublicImageUploadPreparer.Prepared?,
        originalName: String,
        declaredMediaType: String,
        type: FileType,
        uploader: User,
    ): File {
        val sha256 = bytes.open().use(ContentAddress::of)
        val filename = if (prepared == null) {
            StoredFileNames.hashedName(sha256, originalName)
        } else {
            "$sha256.webp"
        }
        val key = StoredFileNames.keyOf(type.directory, filename)
        val mediaType = prepared?.mediaType
            ?: declaredMediaType.ifBlank { MediaTypes.ofName(filename) }

        log.info("Storing {} at {}", sanitizeForLog(originalName), sanitizeForLog(key))

        val size = blobs.put(key, bytes.open())

        val entity = repository.findByPath(key).orElse(null) ?: File(
            name = originalName,
            path = key,
            uploader = uploader,
            mediaType = mediaType,
            size = null,
            type = type,
        )

        populateAfterStore(
            file = entity,
            uploader = uploader,
            name = originalName,
            key = key,
            size = size,
            mediaType = mediaType,
            width = prepared?.width,
            height = prepared?.height,
        )
        entity.type = type

        val stored = if (entity.id != null) update(entity) else create(entity)
        // The widths this picture is served at, written now rather than at the first
        // request for one: a converter run while somebody is waiting for an image is a
        // request that waits for a subprocess.
        imageRenditions.derive(stored)
        return stored
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
     * A stored picture of exactly this kind, or nothing.
     *
     * What a save names when it puts a picture on a record; the kind has to match, so a banner
     * field takes a banner. Absence is an answer rather than a failure — the caller is a write
     * being validated, and has its own words for a picture nobody stored.
     */
    @Transactional(readOnly = true)
    fun findPublicImage(path: String, type: FileType): File? =
        repository.findByPath(path).orElse(null)?.takeIf { it.type == type && it.type.publiclyReadable }

    private fun populateAfterStore(
        file: File,
        uploader: User,
        name: String,
        key: String,
        size: Long,
        mediaType: String,
        width: Int? = null,
        height: Int? = null,
    ) {
        file.name = name
        file.mediaType = mediaType
        file.uploader = uploader
        file.size = size
        file.path = key
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
            blobs.open(key).use(ImageDimensions::of)?.let { measured ->
                file.width = measured.width
                file.height = measured.height
            }
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
        blobs.delete(path)
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileService::class.java)
    }
}

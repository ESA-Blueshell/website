package net.blueshell.api.domain.file.application

import jakarta.annotation.PostConstruct
import net.blueshell.api.domain.file.application.event.FileDeleted
import net.blueshell.api.domain.file.application.exception.EmptyFileException
import net.blueshell.api.domain.file.application.exception.FileNotFoundException
import net.blueshell.api.domain.file.application.exception.FileStorageException
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.file.persistence.repository.FileRepository
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.service.BaseModelService
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
    private val users: UserService
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

        try {
            Files.createDirectories(rootLocation.resolve(type.directory))

            val tmp = Files.createTempFile(rootLocation, "upload-", ".tmp")
            val md = MessageDigest.getInstance("SHA-256")

            multipart.inputStream.use { `in` ->
                DigestInputStream(`in`, md).use { dis ->
                    Files.newOutputStream(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                        .use { out ->
                            dis.transferTo(out)
                        }
                }
            }
            val sha256 = HexFormat.of().formatHex(md.digest())

            val hashedFilename = buildHashedFilename(sha256, multipart.originalFilename ?: "file")
            val path = type.directory + "/" + hashedFilename
            val fullPath = rootLocation.resolve(path).normalize()

            log.info("Storing {} at {}", multipart.originalFilename, fullPath)

            if (Files.exists(fullPath)) {
                Files.deleteIfExists(tmp)
            } else {
                try {
                    Files.move(tmp, fullPath, StandardCopyOption.ATOMIC_MOVE)
                } catch (ignore: FileAlreadyExistsException) {
                    Files.deleteIfExists(tmp)
                }
            }

            var entity = repository.findByPath(path).orElse(null)
            if (entity == null) {
                entity = File()
            }

            val mediaType = resolveMediaType(hashedFilename, fullPath, multipart.contentType ?: "")
            populateAfterStore(entity, multipart.originalFilename ?: "file", fullPath, path, mediaType)
            entity.type = type

            return if (entity.id != null) {
                update(entity)
            } else {
                create(entity)
            }
        } catch (e: IOException) {
            throw FileStorageException("Failed to store file", e)
        } catch (e: NoSuchAlgorithmException) {
            throw FileStorageException("SHA-256 not available", e)
        }
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

    private fun populateAfterStore(file: File, name: String, fullPath: Path, path: String, mediaType: String) {
        file.name = name
        file.mediaType = mediaType
        val currentUserId = currentUserProvider.currentUser()?.id
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user")
        file.uploader = users.findById(currentUserId)
        try {
            file.size = Files.size(fullPath)
        } catch (e: IOException) {
            throw RuntimeException("Could not read file size for: $path", e)
        }
        file.path = path
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
        return repository.findFirstBy_eventBannersIdEventId(eventId).orElseThrow {
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

    fun deleteFromStorage(file: File) {
        deleteFromStoragePath(file.path)
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileService::class.java)
    }
}

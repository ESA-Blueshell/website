package net.blueshell.api.service

import jakarta.annotation.PostConstruct
import jakarta.ws.rs.BadRequestException
import net.blueshell.api.base.BaseModelService
import net.blueshell.api.common.enums.FileType
import net.blueshell.api.mapper.FileMapper
import net.blueshell.api.model.File
import net.blueshell.api.repository.FileRepository
import net.blueshell.api.service.event.EventBannerService
import net.blueshell.api.service.event.EventService
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
    private val fileMapper: FileMapper,
    private val events: EventService,
    private val banners: EventBannerService,
    @Value("\${storage.location}") storageLocation: String
) : BaseModelService<File, FileRepository>(fileRepository) {
    private val rootLocation: Path
    private val assetsLocation: Path = Paths.get("assets")


    init {
        this.rootLocation = Paths.get(storageLocation)
    }

    @Transactional(readOnly = true)
    fun findByName(name: String): File {
        return repository!!.findByName(name).orElseThrow<ResponseStatusException>(Supplier {
            ResponseStatusException(
                HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(name)
            )
        })
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
        if (multipart == null || multipart.isEmpty) {
            throw BadRequestException("Empty file")
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

            val hashedFilename = fileMapper.buildHashedFilename(sha256, multipart.originalFilename)
            val path = type.directory + "/" + hashedFilename
            val fullPath = rootLocation.resolve(path).normalize()

            FileService.log.info("Storing {} at {}", multipart.originalFilename, fullPath)

            if (Files.exists(fullPath)) {
                Files.deleteIfExists(tmp)
            } else {
                try {
                    Files.move(tmp, fullPath, StandardCopyOption.ATOMIC_MOVE)
                } catch (ignore: FileAlreadyExistsException) {
                    Files.deleteIfExists(tmp)
                }
            }

            var entity = repository!!.findByPath(path).orElse(null)
            if (entity == null) {
                entity = File()
            }

            val mediaType = fileMapper.resolveMediaType(hashedFilename, fullPath, multipart.contentType)
            fileMapper.populateAfterStore(entity, multipart.originalFilename, fullPath, path, mediaType)
            entity.type = type

            if (entity.id != null) {
                return update(entity)
            } else {
                return create(entity)
            }
        } catch (e: IOException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e)
        } catch (e: NoSuchAlgorithmException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 not available", e)
        }
    }

    private fun loadAsResource(file: File): Resource {
        try {
            val filePath = rootLocation.resolve(file.path)
            val resource: Resource = UrlResource(filePath.toUri())
            if (resource.exists() || resource.isReadable) return resource
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "File not found with name: %s".formatted(file.name)
            )
        } catch (e: MalformedURLException) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "File not found with name: %s".formatted(file.name)
            )
        }
    }

    private fun loadAssetAsResource(filename: String): Resource {
        try {
            val filePath = assetsLocation.resolve(filename)
            val resource: Resource = UrlResource(filePath.toUri())
            if (resource.exists() || resource.isReadable) return resource
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(filename))
        } catch (e: MalformedURLException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with name: %s".formatted(filename))
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
            .body<Resource>(resource)
    }

    fun prepareAssetResponse(filename: String): ResponseEntity<Resource> {
        val resource = loadAssetAsResource(filename)
        val headers = HttpHeaders()
        headers.contentType = MediaType.valueOf(fileMapper.detectContentTypeForAsset(filename, resource))
        headers.contentDisposition = ContentDisposition.attachment().filename(filename).build()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(10, TimeUnit.DAYS).cachePublic())
            .headers(headers)
            .body<Resource>(resource)
    }

    fun findByEventBannerId(bannerId: Long): File {
        return repository!!.findByEventBanners_Id(bannerId).orElseThrow<ResponseStatusException>(Supplier {
            ResponseStatusException(
                HttpStatus.NOT_FOUND, "Event banner not found with id: %s".formatted(bannerId)
            )
        })
    }

    fun deleteFromStorage(file: File) {
        val fullPath = rootLocation.resolve(file.path).normalize()

        try {
            if (Files.exists(fullPath)) {
                Files.deleteIfExists(fullPath)
            }
        } catch (e: IOException) {
            FileService.log.error("Failed to delete file {}", fullPath, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileService::class.java)
    }
}

package net.blueshell.api.file.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.constraints.NotNull
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.api.Image
import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.api.asImage
import net.blueshell.api.file.domain.NotAPublicImageException
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.web.BaseController
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@Tag(name = "Files")
class FileController(
    service: FileService
) : BaseController<FileService>(service) {
    /**
     * A file of a publicly readable kind, sent inline for a page to draw.
     *
     * Anything else answers 404 rather than 403: whether a file exists is not something an
     * anonymous caller has any business learning.
     */
    @GetMapping(PublicFileUrls.MAPPING)
    @PermitAll
    fun downloadPublicFile(
        @PathVariable directory: String,
        @PathVariable filename: String,
    ): ResponseEntity<Resource> = service.preparePublicFileResponse(
        service.findPubliclyReadable(PublicFileUrls.pathOf(directory, filename)),
    )

    /**
     * A picture meant to be seen, stored so that a save can point at it.
     *
     * One endpoint rather than one per record: storing and applying are separate, so cancelling
     * a dialog leaves every record as it was, and the bytes it left behind stay — reference
     * counting across every table that can point at a file is a larger mechanism than the
     * problem deserves. Admits only publicly readable kinds, so it cannot stash a private
     * document behind an open route.
     */
    @PostMapping(value = [PublicFileUrls.UPLOAD], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    fun uploadPublicImage(
        @RequestParam type: FileType,
        @RequestPart("file") @NotNull(message = "File is required") file: MultipartFile,
    ): Image {
        if (!type.publiclyReadable) throw NotAPublicImageException(type)
        return service.storeMultipart(file, type).asImage()
    }

    @GetMapping("/events/{eventId}/banners")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'read')")
    fun downloadEventBanner(@PathVariable eventId: Long): ResponseEntity<Resource> {
        return service.prepareFileResponse(service.findByBannerEventId(eventId))
    }

    @PostMapping(value = ["/events/banners"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize("hasPermission('__NO_TARGET__', 'EventBanner', 'write')")
    fun uploadEventBanner(
        @RequestPart("file") @NotNull(message = "File is required") @FileSize(max = 10 * 1024 * 1024) @AllowedContentTypes(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif"
        ) file: @NotNull(message = "File is required") MultipartFile
    ): FileResponse {
        return service.storeMultipart(file, FileType.EVENT_BANNER).asResponse()
    }
}


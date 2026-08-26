package net.blueshell.api.domain.file.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.domain.file.web.dto.response.FileResponse
import net.blueshell.api.domain.file.web.mapping.response.asResponse
import net.blueshell.api.domain.file.web.validation.AllowedContentTypes
import net.blueshell.api.domain.file.web.validation.FileSize
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

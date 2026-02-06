package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import net.blueshell.api.common.enums.FileType
import net.blueshell.api.controller.base.BaseController
import net.blueshell.api.dto.FileDTO
import net.blueshell.api.mapper.FileMapper
import net.blueshell.api.repository.FileRepository
import net.blueshell.api.service.FileService
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@Tag(name = "Files")
class FileController(service: FileService, repository: FileRepository, private val fileMapper: FileMapper) :
    BaseController<FileService, FileRepository>(service, repository) {
    @GetMapping("/events/{eventId}/banners")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#eventId, 'Event', 'read')")
    fun downloadEventBanner(@PathVariable eventId: Long): ResponseEntity<Resource> {
        val file = service.findByBannerEventId(eventId)
        return service.prepareFileResponse(file)
    }

    @PostMapping(value = ["/events/banners"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize("hasAuthority('COMMITTEE')")
    fun uploadEventBanner(
        @RequestPart("file") @NotNull(message = "File is required") @net.blueshell.api.validation.file.FileSize(max = 2 * 1024 * 1024) @net.blueshell.api.validation.file.AllowedContentTypes(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif"
        ) file: @NotNull(message = "File is required") MultipartFile
    ): FileDTO {
        val stored = service.storeMultipart(file, FileType.EVENT_BANNER)
        return fileMapper.toDTO(stored)
    }
}

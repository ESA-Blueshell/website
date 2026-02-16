package net.blueshell.api.domain.file.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.file.command.DownloadEventBannerCommand
import net.blueshell.api.domain.file.command.UploadEventBannerCommand
import net.blueshell.api.domain.file.web.dto.FileResponse
import net.blueshell.api.domain.file.web.mapping.asResponse
import net.blueshell.api.domain.file.web.validation.AllowedContentTypes
import net.blueshell.api.domain.file.web.validation.FileSize
import net.blueshell.api.shared.command.CommandBus
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
    service: net.blueshell.api.domain.file.application.FileService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.file.application.FileService>(service) {
    @GetMapping("/events/{eventId}/banners")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'read')")
    fun downloadEventBanner(@PathVariable eventId: Long): ResponseEntity<Resource> {
        return commandBus.dispatch(DownloadEventBannerCommand(eventId))
    }

    @PostMapping(value = ["/events/banners"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize("hasPermission(null, 'EventBanner', 'create')")
    fun uploadEventBanner(
        @RequestPart("file") @NotNull(message = "File is required") @FileSize(max = 2 * 1024 * 1024) @AllowedContentTypes(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif"
        ) file: @NotNull(message = "File is required") MultipartFile
    ): FileResponse {
        val stored = commandBus.dispatch(UploadEventBannerCommand(file))
        return stored.asResponse()
    }
}

package net.blueshell.api.domain.file.command

import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.command.Command
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.multipart.MultipartFile

data class DownloadEventBannerCommand(
    @field:NotNull(message = "Event ID is required")
    val eventId: Long
) : Command<ResponseEntity<Resource>>

data class UploadEventBannerCommand(
    @field:NotNull(message = "File must be provided")
    val file: MultipartFile
) : Command<File>

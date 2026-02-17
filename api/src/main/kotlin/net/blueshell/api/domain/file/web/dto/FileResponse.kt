package net.blueshell.api.domain.file.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.FileType
import java.time.Instant

@Schema(name = "FileResponse")
data class FileResponse(
    var id: Long,

    @field:NotBlank(message = "File name cannot be blank.")
    @field:Size(max = 255, message = "File name cannot exceed 255 characters.")
    var name: String,
    var mediaType: String,
    var size: Long? = null,
    var type: FileType,
    var path: String,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)

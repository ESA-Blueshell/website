package net.blueshell.api.domain.file.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.FileType
import java.time.Instant

@Schema(name = "File")
data class FileDTO(
    var id: Long? = null,

    @field:NotBlank(message = "File name cannot be blank.")
    @field:Size(max = 255, message = "File name cannot exceed 255 characters.")
    var name: String? = null,
    var mediaType: String? = null,
    var size: Long? = null,
    var type: FileType? = null,
    var path: String? = null,
    var version: Long? = null,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
)

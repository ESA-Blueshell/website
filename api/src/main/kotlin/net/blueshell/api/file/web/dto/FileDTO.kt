package net.blueshell.api.file.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "File")
data class FileDTO(
    @field:NotBlank(message = "File name cannot be blank.")
    @field:Size(max = 255, message = "File name cannot exceed 255 characters.")
    var name: String,
    var mediaType: String? = null,
    var size: Long? = null,
    var type: FileType? = null
) : AuditedAutoIdDTO()

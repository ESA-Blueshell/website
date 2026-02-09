package net.blueshell.api.file.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "File")
data class FileDTO(
    var name: String? = null,
    var mediaType: String? = null,
    var size: Long? = null,
    var type: FileType? = null
) : AuditedAutoIdDTO()

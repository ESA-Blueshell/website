package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.common.enums.FileType
import net.blueshell.api.dto.base.AuditedAutoIdDTO

@Schema(name = "File")
data class FileDTO(
    var name: String? = null,
    var mediaType: String? = null,
    var size: Long? = null,
    var type: FileType? = null
) : AuditedAutoIdDTO()

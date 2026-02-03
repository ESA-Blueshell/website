package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.FileType

@Schema(name = "File")
data class FileDTO(
    var name: String? = null,
    var mediaType: String? = null,
    var size: Long? = null,
    var type: FileType? = null
) : BaseDTO()

package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.FileType
@Schema(name = "File")
class FileDTO : BaseDTO() {
    val name: String? = null
    val mediaType: String? = null
    val size: Long? = null
    val type: FileType? = null
}
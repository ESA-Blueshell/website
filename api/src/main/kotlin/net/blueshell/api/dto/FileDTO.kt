package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.FileType

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "File")
class FileDTO : BaseDTO() {
    private val id: Long? = null
    private val name: String? = null
    private val mediaType: String? = null
    private val size: Long? = null
    private val type: FileType? = null
}
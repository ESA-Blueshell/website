package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
@Schema(name = "Picture")
class PictureDTO : BaseDTO() {
    val name: String? = null
    val url: String? = null
    val uploaderId: Long = 0
    val eventId: Long = 0
}

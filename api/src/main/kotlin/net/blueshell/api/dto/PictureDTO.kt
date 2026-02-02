package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Picture")
class PictureDTO : BaseDTO() {
    private val id: Long? = null
    private val name: String? = null
    private val url: String? = null
    private val uploaderId: Long = 0
    private val eventId: Long = 0
}

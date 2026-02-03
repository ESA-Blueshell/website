package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO

@Schema(name = "Picture")
data class PictureDTO(
    var name: String? = null,
    var url: String? = null,
    var uploaderId: Long = 0,
    var eventId: Long = 0
) : BaseDTO()

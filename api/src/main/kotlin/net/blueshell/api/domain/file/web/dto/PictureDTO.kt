package net.blueshell.api.domain.file.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "Picture")
data class PictureDTO(
    var id: Long? = null,
    var name: String? = null,
    var url: String? = null,

    @field:NotNull
    var uploaderId: Long? = null,

    @field:NotNull
    var eventId: Long? = null,
    var version: Long? = null,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
)

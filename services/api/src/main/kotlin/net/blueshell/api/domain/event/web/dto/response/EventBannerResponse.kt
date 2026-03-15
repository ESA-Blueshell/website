package net.blueshell.api.domain.event.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "EventBannerResponse")
data class EventBannerResponse(
    @field:NotNull
    var eventId: Long,

    @field:NotNull
    var fileId: Long,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)

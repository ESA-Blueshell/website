package net.blueshell.api.domain.event.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "GuestResponse")
data class GuestResponse(
    @field:NotNull
    var id: Long,
    @field:NotNull
    var name: String,
    @field:NotNull
    var email: String,
    @field:NotNull
    var discord: String,
    var phoneNumber: String? = null,
    @field:NotNull
    var accessToken: String,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)

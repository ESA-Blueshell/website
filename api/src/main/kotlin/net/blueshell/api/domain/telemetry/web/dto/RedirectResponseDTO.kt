package net.blueshell.api.domain.telemetry.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(name = "RedirectResponse")
data class RedirectResponseDTO(
    @field:NotNull
    var path: String? = null
)

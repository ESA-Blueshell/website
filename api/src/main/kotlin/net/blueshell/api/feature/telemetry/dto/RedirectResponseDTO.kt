package net.blueshell.api.feature.telemetry.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(name = "RedirectResponse")
data class RedirectResponseDTO(
    @field:NotNull
    var path: String?
)

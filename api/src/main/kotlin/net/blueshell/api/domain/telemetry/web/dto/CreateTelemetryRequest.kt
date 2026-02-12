package net.blueshell.api.domain.telemetry.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.PlatformType

@Schema(name = "CreateTelemetryRequest")
data class CreateTelemetryRequest(
    @field:NotBlank
    var url: String? = null,

    @field:NotNull
    var platform: PlatformType? = null
)

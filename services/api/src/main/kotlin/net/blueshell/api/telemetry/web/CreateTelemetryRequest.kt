package net.blueshell.api.telemetry.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.shared.enums.PlatformType

@Schema(name = "CreateTelemetryRequest")
data class CreateTelemetryRequest(
    @field:NotBlank
    var url: String,

    var platform: PlatformType
)

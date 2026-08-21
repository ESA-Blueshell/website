package net.blueshell.api.domain.telemetry.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.shared.enums.PlatformType

@Schema(name = "CreateTelemetryRequest")
data class CreateTelemetryRequest(
    @field:NotBlank
    var url: String,

    var platform: PlatformType
)

package net.blueshell.api.domain.telemetry.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Redirect")
data class RedirectDTO(
    @field:NotNull
    var telemetry: TelemetryDTO? = null
) : AuditedAutoIdDTO()

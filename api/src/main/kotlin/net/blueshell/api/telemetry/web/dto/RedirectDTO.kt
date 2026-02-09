package net.blueshell.api.telemetry.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Redirect")
data class RedirectDTO(
    var telemetry: TelemetryDTO? = null
) : AuditedAutoIdDTO()

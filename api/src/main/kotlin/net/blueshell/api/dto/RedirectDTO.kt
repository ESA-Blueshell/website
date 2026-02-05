package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.dto.base.AuditedAutoIdDTO

@Schema(name = "Redirect")
data class RedirectDTO(
    var telemetry: TelemetryDTO? = null
) : AuditedAutoIdDTO()

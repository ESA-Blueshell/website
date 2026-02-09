package net.blueshell.api.telemetry.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Telemetry")
data class TelemetryDTO(
    var url: String? = null,
    var platform: PlatformType? = null
) : AuditedAutoIdDTO()

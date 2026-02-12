package net.blueshell.api.domain.telemetry.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.shared.enums.PlatformType

@Schema(name = "TelemetryResponse")
data class TelemetryResponse(
    var url: String? = null,
    var platform: PlatformType? = null
) : AuditedAutoIdDTO()

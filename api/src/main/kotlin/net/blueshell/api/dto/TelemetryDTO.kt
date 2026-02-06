package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.dto.base.AuditedAutoIdDTO

@Schema(name = "Telemetry")
data class TelemetryDTO(
    var url: String? = null,
    var platform: PlatformType? = null
) : AuditedAutoIdDTO()

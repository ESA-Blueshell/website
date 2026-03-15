package net.blueshell.api.domain.telemetry.web.mapping.response

import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.telemetry.web.dto.response.TelemetryResponse

fun Telemetry.asResponse(): TelemetryResponse =
    TelemetryResponse(
        id = this.id!!,
        url = this.url,
        platform = this.platform,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

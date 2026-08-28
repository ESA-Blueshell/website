package net.blueshell.api.telemetry.web

import net.blueshell.api.telemetry.persistence.Telemetry

fun Telemetry.asResponse(): TelemetryResponse =
    TelemetryResponse(
        id = this.id!!,
        url = this.url,
        platform = this.platform,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

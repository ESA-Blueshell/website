package net.blueshell.api.telemetry.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.PlatformType
import java.time.Instant

@Schema(name = "TelemetryResponse")
data class TelemetryResponse(
    var id: Long,
    var url: String,
    var platform: PlatformType,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)

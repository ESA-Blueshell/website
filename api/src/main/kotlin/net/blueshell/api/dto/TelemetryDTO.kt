package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.PlatformType
import java.time.Instant
@Schema(name = "Telemetry")
class TelemetryDTO : BaseDTO() {
    val url: String? = null
    val platform: PlatformType? = null
}


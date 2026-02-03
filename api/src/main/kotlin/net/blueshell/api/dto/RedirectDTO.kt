package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
import java.time.Instant
@Schema(name = "Redirect")
class RedirectDTO : BaseDTO() {
    val telemetry: TelemetryDTO? = null
}


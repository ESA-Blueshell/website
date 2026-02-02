package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import java.time.Instant


@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Redirect")
class RedirectDTO : BaseDTO() {
    private val createdAt: Instant? = null
    private val telemetry: TelemetryDTO? = null
}


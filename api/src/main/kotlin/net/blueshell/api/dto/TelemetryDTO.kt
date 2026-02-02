package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.PlatformType
import java.time.Instant


@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Telemetry")
class TelemetryDTO : BaseDTO() {
    private val url: String? = null
    private val platform: PlatformType? = null
    private val createdAt: Instant? = null
}


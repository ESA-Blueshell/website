package net.blueshell.api.factory.dto

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.telemetry.api.dto.TelemetryDTO
import org.springframework.stereotype.Component

/**
 * Factory for TelemetryDTO test instances.
 */
@Component
class TelemetryDTOFactory : BaseDtoFactory<TelemetryDTO>() {

    override fun targetType(): Class<TelemetryDTO> = TelemetryDTO::class.java

    override fun createBasic(): TelemetryDTO {
        val dto = TelemetryDTO()
        dto.platform = PlatformType.FACEBOOK
        dto.url = "https://example.com/${nextId()}"
        dto.createdAt = now()
        return dto
    }
}

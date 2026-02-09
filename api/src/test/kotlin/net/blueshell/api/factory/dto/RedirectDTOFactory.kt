package net.blueshell.api.factory.dto

import net.blueshell.api.telemetry.web.dto.RedirectDTO
import org.springframework.stereotype.Component

/**
 * Factory for RedirectDTO test instances.
 */
@Component
class RedirectDTOFactory(
    private val telemetryFactory: TelemetryDTOFactory
) : BaseDtoFactory<RedirectDTO>() {

    override fun targetType(): Class<RedirectDTO> = RedirectDTO::class.java

    override fun createBasic(): RedirectDTO {
        val dto = RedirectDTO()
        dto.createdAt = now()
        dto.telemetry = telemetryFactory.createBasic()
        return dto
    }
}

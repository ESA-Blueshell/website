package net.blueshell.api.factory.dto.response

import net.blueshell.api.factory.dto.BaseDtoFactory
import net.blueshell.api.domain.telemetry.web.dto.RedirectResponseDTO
import org.springframework.stereotype.Component

/**
 * Factory for RedirectResponseDTO test instances.
 */
@Component
class RedirectResponseDTOFactory : BaseDtoFactory<RedirectResponseDTO>() {

    override fun targetType(): Class<RedirectResponseDTO> = RedirectResponseDTO::class.java

    override fun createBasic(): RedirectResponseDTO = RedirectResponseDTO("/test/${nextId()}")
}

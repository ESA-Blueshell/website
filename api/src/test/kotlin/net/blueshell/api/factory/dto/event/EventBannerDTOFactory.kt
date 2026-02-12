package net.blueshell.api.factory.dto.event

import net.blueshell.api.domain.event.web.dto.EventBannerDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import net.blueshell.api.factory.dto.FileDTOFactory
import org.springframework.stereotype.Component

/**
 * Factory for EventBannerDTO test instances.
 */
@Component
class EventBannerDTOFactory(
    private val fileFactory: FileDTOFactory
) : BaseDtoFactory<EventBannerDTO>() {

    override fun targetType(): Class<EventBannerDTO> = EventBannerDTO::class.java

    override fun createBasic(): EventBannerDTO {
        val dto = EventBannerDTO()
        return dto
    }
}

package net.blueshell.api.factory.dto.event

import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import org.springframework.stereotype.Component

/**
 * Factory for EventDTO test instances.
 */
@Component
class EventDTOFactory(
    private val bannerFactory: EventBannerDTOFactory,
    private val surveyFactory: SurveyDTOFactory
) : BaseDtoFactory<EventDTO>() {

    override fun targetType(): Class<EventDTO> = EventDTO::class.java

    override fun createBasic(): EventDTO {
        val start = now().plusSeconds(3600)
        return EventDTO(
            nextId(),
            unique("Event"),
            "Test event description",
            "Test Location",
            start,
            start.plusSeconds(3600),
            0.0,
            10.0,
            true,
            false,
            true,
            bannerFactory.createBasic(),
            0L,
            surveyFactory.createBasic()
        )
    }
}

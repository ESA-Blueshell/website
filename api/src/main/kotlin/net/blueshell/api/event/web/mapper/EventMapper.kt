package net.blueshell.api.event.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.web.mapper.SurveyMapper
import net.blueshell.api.event.persistence.Event
import org.springframework.stereotype.Component

@Konverter
interface EventKonverter {
    @Konvert(
        mappings = [
            Mapping(target = "banner", ignore = true),
            Mapping(target = "signUpForm", ignore = true),
        ]
    )
    fun toDTO(event: Event): EventDTO

    @Konvert(
        mappings = [
            Mapping(target = "banner", ignore = true),
            Mapping(target = "signUpForm", ignore = true),
        ]
    )
    fun fromDTO(dto: EventDTO): Event
}

@Component
class EventMapper(
    private val eventBannerMapper: EventBannerMapper,
    private val surveyMapper: SurveyMapper
) : BaseMapper<Event, EventDTO>() {
    private val konverter = konverter<EventKonverter>()

    override fun fromDTO(dto: EventDTO): Event = fromDTO(dto, Event())

    fun fromDTO(dto: EventDTO, event: Event): Event {
        val mapped = konverter.fromDTO(dto)
        event.committeeId = mapped.committeeId
        event.title = mapped.title
        event.description = mapped.description
        event.location = mapped.location
        event.startTime = requireNotNull(dto.startTime)
        event.endTime = requireNotNull(dto.endTime)
        event.memberPrice = mapped.memberPrice
        event.publicPrice = mapped.publicPrice
        event.membersOnly = mapped.membersOnly
        event.banner = dto.banner?.let { eventBannerMapper.fromDTO(it) }
        event.signUpForm = dto.signUpForm?.let { surveyMapper.fromDTO(it) }
        event.signUp = mapped.signUp
        dto.signUpCount?.let { event.signUpCount = it }
        dto.version?.let { event.version = it }
        afterFromDTO(dto, event)
        return event
    }

    private fun afterFromDTO(dto: EventDTO, event: Event) {
        if (hasAuthority(Role.BOARD)) {
            event.approved = dto.approved
        } else {
            event.approved = false
        }
    }

    override fun toDTO(event: Event): EventDTO {
        val dto = konverter.toDTO(event)
        dto.banner = event.banner?.let { eventBannerMapper.toDTO(it) }
        dto.signUpForm = event.signUpForm?.let { surveyMapper.toDTO(it) }
        return dto
    }
}

fun Event.asDTO(mapper: EventMapper): EventDTO = mapper.toDTO(this)

fun EventDTO.asEntity(mapper: EventMapper): Event = mapper.fromDTO(this)

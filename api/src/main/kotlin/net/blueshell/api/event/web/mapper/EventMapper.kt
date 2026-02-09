package net.blueshell.api.event.web.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.web.mapper.SurveyMapper
import net.blueshell.api.event.persistence.Event
import org.springframework.stereotype.Component

@Component
class EventMapper(
    private val eventBannerMapper: EventBannerMapper,
    private val surveyMapper: SurveyMapper
) : BaseMapper<Event, EventDTO>() {
    override fun fromDTO(dto: EventDTO): Event = fromDTO(dto, Event())

    fun fromDTO(dto: EventDTO, event: Event): Event {
        event.committeeId = dto.committeeId
        event.title = dto.title
        event.description = dto.description
        event.location = dto.location
        event.startTime = requireNotNull(dto.startTime)
        event.endTime = requireNotNull(dto.endTime)
        event.memberPrice = dto.memberPrice
        event.publicPrice = dto.publicPrice
        event.membersOnly = dto.membersOnly
        event.banner = dto.banner?.let { eventBannerMapper.fromDTO(it) }
        event.signUpForm = dto.signUpForm?.let { surveyMapper.fromDTO(it) }
        event.signUp = dto.signUp
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
        return EventDTO(
            committeeId = event.committeeId,
            title = event.title,
            description = requireNotNull(event.description),
            location = event.location,
            startTime = event.startTime,
            endTime = event.endTime,
            memberPrice = event.memberPrice,
            publicPrice = event.publicPrice,
            approved = event.approved,
            membersOnly = event.membersOnly,
            signUp = event.signUp,
            banner = event.banner?.let { eventBannerMapper.toDTO(it) },
            signUpCount = event.signUpCount,
            signUpForm = event.signUpForm?.let { surveyMapper.toDTO(it) }
        ).also { dto ->
            dto.id = event.id
            dto.version = event.version
        }
    }
}

fun Event.asDTO(mapper: EventMapper): EventDTO = mapper.toDTO(this)

fun EventDTO.asEntity(mapper: EventMapper): Event = mapper.fromDTO(this)

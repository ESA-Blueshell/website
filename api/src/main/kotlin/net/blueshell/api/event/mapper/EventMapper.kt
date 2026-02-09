package net.blueshell.api.event.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.event.dto.EventDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.mapper.SurveyMapper
import net.blueshell.api.event.model.Event
import org.mapstruct.*

@Mapper(componentModel = "spring", uses = [EventBannerMapper::class, SurveyMapper::class])
abstract class EventMapper : BaseMapper<Event, EventDTO>() {
    @Mapping(target = "committeeId")
    @Mapping(target = "title")
    @Mapping(target = "description")
    @Mapping(target = "location")
    @Mapping(target = "startTime")
    @Mapping(target = "endTime")
    @Mapping(target = "memberPrice")
    @Mapping(target = "publicPrice")
    @Mapping(target = "membersOnly")
    @Mapping(target = "banner")
    @Mapping(target = "signUpForm")
    @Mapping(target = "signUp")
    @Mapping(target = "signUpCount")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: EventDTO, @MappingTarget event: Event): Event

    @AfterMapping
    protected fun afterFromDTO(dto: EventDTO, @MappingTarget event: Event) {
        if (hasAuthority(Role.BOARD)) {
            event.approved = dto.approved
        } else {
            event.approved = false
        }
    }

    @Mapping(target = "id")
    @Mapping(target = "committeeId")
    @Mapping(target = "title")
    @Mapping(target = "description")
    @Mapping(target = "location")
    @Mapping(target = "startTime")
    @Mapping(target = "endTime")
    @Mapping(target = "memberPrice")
    @Mapping(target = "publicPrice")
    @Mapping(target = "approved")
    @Mapping(target = "membersOnly")
    @Mapping(target = "banner")
    @Mapping(target = "signUpForm")
    @Mapping(target = "signUp")
    @Mapping(target = "signUpCount")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(event: Event): EventDTO
}

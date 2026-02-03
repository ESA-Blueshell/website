package net.blueshell.api.mapper.event

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.common.enums.Role
import net.blueshell.api.dto.event.EventDTO
import net.blueshell.api.mapper.survey.SurveyMapper
import net.blueshell.api.model.event.Event
import org.mapstruct.*

@Mapper(componentModel = "spring", uses = [EventBannerMapper::class, SurveyMapper::class])
abstract class EventMapper : BaseMapper<Event?, EventDTO?>() {
    @Mapping(target = "id")
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
    abstract fun fromDTO(dto: EventDTO?, @MappingTarget event: Event?): Event?

    @AfterMapping
    protected fun afterFromDTO(dto: EventDTO, @MappingTarget event: Event) {
        if (event.getBanner() != null) {
            event.getBanner().setEvent(event)
        }
        if (hasAuthority(Role.BOARD)) {
            event.setApproved(dto.approved)
        } else {
            event.setApproved(false)
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
    abstract override fun toDTO(event: Event?): EventDTO?
}

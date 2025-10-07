package net.blueshell.api.mapper.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.mapper.survey.SurveyMapper;
import net.blueshell.api.model.event.Event;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Mapper(componentModel = "spring", uses = {EventBannerMapper.class, SurveyMapper.class})
public abstract class EventMapper extends BaseMapper<Event, EventDTO> {

    static LocalDateTime map(OffsetDateTime t) {
        return t == null ? null
                : t.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    static OffsetDateTime map(LocalDateTime t) {
        return t == null ? null
                : t.atZone(ZoneId.systemDefault()).toOffsetDateTime();
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
    @Mapping(target = "membersOnly")
    @Mapping(target = "banner")
    @Mapping(target = "signUpForm")
    @Mapping(target = "signUp")
    @Mapping(target = "signUpCount")
    @BeanMapping(ignoreByDefault = true)
    public abstract Event fromDTO(EventDTO dto, @MappingTarget Event event);

    @AfterMapping
    protected void afterFromDTO(EventDTO dto, @MappingTarget Event event) {
        if (event.getCreatorId() == null) {
            event.setCreatorId(getPrincipal().getId());
        }
        event.setLastEditorId(getPrincipal().getId());
        if (event.getBanner() != null) {
            event.getBanner().setEvent(event);
        }
        if (hasAuthority(Role.BOARD)) {
            event.setApproved(dto.isApproved());
        } else {
            event.setApproved(false);
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
    @BeanMapping(ignoreByDefault = true)
    public abstract EventDTO toDTO(Event event);
}

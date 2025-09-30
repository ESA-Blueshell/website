package net.blueshell.api.mapper;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.model.Event;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, uses = {FileMapper.class, FormQuestionMapper.class})
public abstract class EventMapper extends BaseMapper<Event, EventDTO> {

    @Mapping(target = "id")
    @Mapping(target = "committeeId")
    @Mapping(target = "title")
    @Mapping(target = "description")
    @Mapping(target = "location")
    @Mapping(target = "startTime")
    @Mapping(target = "endTime")
    @Mapping(target = "memberPrice")
    @Mapping(target = "publicPrice")
    @Mapping(target = "visible")
    @Mapping(target = "membersOnly")
    @Mapping(target = "signUp")
    @Mapping(target = "banner")
    @Mapping(target = "signUpForm")
    @BeanMapping(ignoreByDefault = true)
    public abstract Event fromDTO(EventDTO dto, @MappingTarget Event event);

    @AfterMapping
    protected void afterFromDTO(EventDTO dto, @MappingTarget Event event) {
        if (event.getCreator() == null) {
            event.setCreatorId(getPrincipal().getId());
        }
        event.setLastEditorId(getPrincipal().getId());
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
    @Mapping(target = "visible")
    @Mapping(target = "membersOnly")
    @Mapping(target = "signUp")
    @Mapping(target = "banner")
    @Mapping(target = "signUpForm")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventDTO toDTO(Event event);

    static LocalDateTime map(OffsetDateTime t) {
        return t == null ? null
                : t.toLocalDateTime();
    }

    static OffsetDateTime map(LocalDateTime t) {
        return t == null ? null
                : t.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}

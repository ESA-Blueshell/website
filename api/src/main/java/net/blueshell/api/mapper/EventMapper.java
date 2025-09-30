package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.mapper.committee.SimpleCommitteeMapper;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.File;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, uses = {SimpleCommitteeMapper.class, FileMapper.class})
public abstract class EventMapper extends BaseMapper<Event, EventDTO> {

    @Autowired
    protected SimpleCommitteeMapper simpleCommitteeMapper;
    @Autowired
    protected FileMapper fileMapper;

    @Mapping(target = "id")
    @Mapping(target = "startTime")
    @Mapping(target = "endTime")
    @Mapping(target = "committeeId")
    @Mapping(target = "committee")
    @Mapping(target = "title")
    @Mapping(target = "description")
    @Mapping(target = "location")
    @Mapping(target = "memberPrice")
    @Mapping(target = "publicPrice")
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

        if (StringUtils.hasText(dto.getStartTime())) {
            OffsetDateTime startTime = OffsetDateTime.parse(dto.getStartTime());
            event.setStartTime(startTime.toLocalDateTime());
        }
        if (StringUtils.hasText(dto.getEndTime())) {
            OffsetDateTime endTime = OffsetDateTime.parse(dto.getEndTime());
            event.setEndTime(endTime.toLocalDateTime());
        }

        if (dto.getBanner() != null) {
            File banner = fileMapper.fromDTO(dto.getBanner());
            event.setBanner(banner);
        }
    }

    @Mapping(target = "id")
    @Mapping(target = "startTime", expression = "java(net.blueshell.api.mapper.EventMapper.toIso(event.getStartTime()))")
    @Mapping(target = "endTime", expression = "java(net.blueshell.api.mapper.EventMapper.toIso(event.getEndTime()))")
    @Mapping(target = "committeeId")
    @Mapping(target = "committee")
    @Mapping(target = "title")
    @Mapping(target = "description")
    @Mapping(target = "location")
    @Mapping(target = "memberPrice")
    @Mapping(target = "publicPrice")
    @Mapping(target = "banner")
    @Mapping(target = "signUpForm")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventDTO toDTO(Event event);

    static String toIso(LocalDateTime t) {
        return t == null ? null
                : t.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @AfterMapping
    public void afterToDTO(Event event, @MappingTarget EventDTO dto) {
        if (event.getCommittee() != null) {
            dto.setCommittee(simpleCommitteeMapper.toDTO(event.getCommittee()));
        }
    }
}

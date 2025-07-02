package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.mapper.committee.SimpleCommitteeMapper;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.File;
import net.blueshell.api.service.CommitteeService;
import net.blueshell.api.service.FileService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Mapper(componentModel = "spring")
public abstract class EventMapper extends BaseMapper<Event, EventDTO> {


    @Autowired
    protected CommitteeService committeeService;
    @Autowired
    protected SimpleCommitteeMapper simpleCommitteeMapper;
    @Autowired
    protected FileService fileService;
    @Autowired
    protected FileMapper fileMapper;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "lastEditor", ignore = true)
    @Mapping(target = "banner", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "googleId", ignore = true)
    @Mapping(target = "feedbacks", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    public abstract Event fromDTO(EventDTO dto);

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

    public abstract EventDTO toDTO(Event event);

    @AfterMapping
    public void afterToDTO(Event event, @MappingTarget EventDTO dto) {
        if (event.getCommittee() != null) {
            dto.setCommittee(simpleCommitteeMapper.toDTO(event.getCommittee()));
        }
    }
}

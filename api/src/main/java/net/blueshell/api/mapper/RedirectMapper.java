package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.RedirectDTO;
import net.blueshell.api.dto.TelemetryDTO;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.model.Telemetry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class RedirectMapper extends BaseMapper<Redirect, RedirectDTO> {

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    public abstract Telemetry fromDTO(TelemetryDTO dto);

    public abstract TelemetryDTO toDTO(Telemetry telemetry);
}

package net.blueshell.telemetry.mapping;

import net.blueshell.common.dto.RedirectDTO;
import net.blueshell.common.dto.TelemetryDTO;
import net.blueshell.db.BaseMapper;
import net.blueshell.telemetry.model.Redirect;
import net.blueshell.telemetry.model.Telemetry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class RedirectMapper extends BaseMapper<Redirect, RedirectDTO> {

    @Mapping(target = "deletedAt", ignore = true)
    public abstract Telemetry fromDTO(TelemetryDTO dto);

    public abstract TelemetryDTO toDTO(Telemetry telemetry);
}

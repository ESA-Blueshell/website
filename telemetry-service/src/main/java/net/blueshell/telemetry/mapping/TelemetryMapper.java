package net.blueshell.telemetry.mapping;

import net.blueshell.common.dto.TelemetryDTO;
import net.blueshell.common.mapper.BaseMapper;
import net.blueshell.telemetry.model.Telemetry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class TelemetryMapper extends BaseMapper<Telemetry, TelemetryDTO> {

    @Mapping(target = "deletedAt", ignore = true)
    public abstract Telemetry fromDTO(TelemetryDTO dto);

    public abstract TelemetryDTO toDTO(Telemetry telemetry);
}

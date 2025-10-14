package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.TelemetryDTO;
import net.blueshell.api.model.Telemetry;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class TelemetryMapper extends BaseMapper<Telemetry, TelemetryDTO> {

    @BeanMapping(ignoreByDefault = true)
    public abstract Telemetry fromDTO(TelemetryDTO dto, @MappingTarget Telemetry telemetry);

    public abstract TelemetryDTO toDTO(Telemetry telemetry);
}

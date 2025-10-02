package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.RedirectDTO;
import net.blueshell.api.dto.TelemetryDTO;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.model.Telemetry;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class RedirectMapper extends BaseMapper<Redirect, RedirectDTO> {

    @BeanMapping(ignoreByDefault = true)
    public abstract Telemetry fromDTO(TelemetryDTO dto, @MappingTarget Telemetry telemetry);

    @BeanMapping(ignoreByDefault = true)
    public abstract TelemetryDTO toDTO(Telemetry telemetry);
}

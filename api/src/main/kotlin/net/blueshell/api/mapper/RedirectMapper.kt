package net.blueshell.api.mapper

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.RedirectDTO
import net.blueshell.api.dto.TelemetryDTO
import net.blueshell.api.model.Redirect
import net.blueshell.api.model.Telemetry
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class RedirectMapper : BaseMapper<Redirect, RedirectDTO>() {
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: TelemetryDTO, @MappingTarget telemetry: Telemetry): Telemetry

    @BeanMapping(ignoreByDefault = true)
    abstract fun toDTO(telemetry: Telemetry): TelemetryDTO
}

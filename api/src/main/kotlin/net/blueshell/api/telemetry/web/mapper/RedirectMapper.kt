package net.blueshell.api.telemetry.web.mapper

import net.blueshell.api.telemetry.web.dto.RedirectDTO
import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.telemetry.persistence.Redirect
import net.blueshell.api.telemetry.persistence.Telemetry
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class RedirectMapper : BaseMapper<Redirect, RedirectDTO>() {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "url")
    @Mapping(target = "platform")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: TelemetryDTO, @MappingTarget telemetry: Telemetry): Telemetry

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "url")
    @Mapping(target = "platform")
    @Mapping(target = "version")
    abstract fun toDTO(telemetry: Telemetry): TelemetryDTO
}

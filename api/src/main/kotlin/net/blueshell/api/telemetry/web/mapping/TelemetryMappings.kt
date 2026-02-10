package net.blueshell.api.telemetry.web.mapping

import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import tech.mappie.api.ObjectMappie

object TelemetryToTelemetryDTOMapper : ObjectMappie<Telemetry, TelemetryDTO>()

object TelemetryDTOToTelemetryMapper : ObjectMappie<TelemetryDTO, Telemetry>()

fun TelemetryDTO.asEntity(existing: Telemetry? = null): Telemetry {
    val mapped = TelemetryDTOToTelemetryMapper.map(this)
    existing?.id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun Telemetry.asDto(): TelemetryDTO = TelemetryToTelemetryDTOMapper.map(this)

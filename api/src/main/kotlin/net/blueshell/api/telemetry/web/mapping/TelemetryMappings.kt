package net.blueshell.api.telemetry.web.mapping

import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import tech.mappie.api.ObjectMappie

object TelemetryToTelemetryDTOMapper : ObjectMappie<Telemetry, TelemetryDTO>()

fun TelemetryDTO.asEntity(telemetry: Telemetry = Telemetry()): Telemetry {
    telemetry.platform = platform
    telemetry.url = url
    telemetry.version = version
    return telemetry
}

fun Telemetry.asDto(): TelemetryDTO = TelemetryToTelemetryDTOMapper.map(this)

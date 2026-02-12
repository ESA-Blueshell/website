package net.blueshell.api.domain.telemetry.web.mapping

import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.telemetry.web.dto.CreateTelemetryRequest
import net.blueshell.api.domain.telemetry.web.dto.TelemetryResponse
import tech.mappie.api.ObjectMappie

object TelemetryToTelemetryResponseMapper : ObjectMappie<Telemetry, TelemetryResponse>()

fun CreateTelemetryRequest.asEntity(telemetry: Telemetry = Telemetry()): Telemetry {
    telemetry.platform = platform!!
    telemetry.url = url!!
    return telemetry
}

fun Telemetry.asResponse(): TelemetryResponse = TelemetryToTelemetryResponseMapper.map(this)

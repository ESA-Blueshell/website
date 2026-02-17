package net.blueshell.api.domain.telemetry.web.mapping.response

import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.telemetry.web.dto.response.TelemetryResponse
import tech.mappie.api.ObjectMappie

object TelemetryToTelemetryResponseMapper : ObjectMappie<Telemetry, TelemetryResponse>() {
    override fun map(from: Telemetry) = mapping {
        TelemetryResponse::id fromValue from.id!!
    }
}

fun Telemetry.asResponse(): TelemetryResponse = TelemetryToTelemetryResponseMapper.map(this)

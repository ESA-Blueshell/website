package net.blueshell.api.domain.telemetry.web.mapping

import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.telemetry.web.dto.response.TelemetryResponse
import tech.mappie.api.ObjectMappie

object TelemetryToTelemetryResponseMapper : ObjectMappie<Telemetry, TelemetryResponse>()

fun Telemetry.asResponse(): TelemetryResponse = TelemetryToTelemetryResponseMapper.map(this)

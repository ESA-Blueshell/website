package net.blueshell.api.telemetry.web.dto

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.telemetry.persistence.Telemetry

@Konverter
interface TelemetryKonverter {
    fun toDTO(telemetry: Telemetry): TelemetryDTO

    fun fromDTO(dto: TelemetryDTO): Telemetry
}

private val telemetryKonverter = Konverter.get<TelemetryKonverter>()

fun TelemetryDTO.asEntity(telemetry: Telemetry = Telemetry()): Telemetry {
    val mapped = telemetryKonverter.fromDTO(this)
    telemetry.url = mapped.url
    telemetry.platform = mapped.platform
    version?.let { telemetry.version = it }
    return telemetry
}

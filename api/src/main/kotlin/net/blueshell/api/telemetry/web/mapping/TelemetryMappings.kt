package net.blueshell.api.telemetry.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.web.dto.TelemetryDTO

@Konverter
interface TelemetryKonverter {
    fun toDTO(telemetry: Telemetry): TelemetryDTO

    fun fromDTO(dto: TelemetryDTO): Telemetry
}

private val telemetryKonverter = Konverter.get<TelemetryKonverter>()

fun TelemetryDTO.asEntity(existing: Telemetry? = null): Telemetry {
    val mapped = telemetryKonverter.fromDTO(this)
    existing?.id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun Telemetry.asDto(): TelemetryDTO = telemetryKonverter.toDTO(this)

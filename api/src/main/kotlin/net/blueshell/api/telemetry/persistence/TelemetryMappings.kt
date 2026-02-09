package net.blueshell.api.telemetry.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import net.blueshell.api.telemetry.web.dto.TelemetryKonverter

private val telemetryKonverter = Konverter.get<TelemetryKonverter>()

fun Telemetry.asDto(): TelemetryDTO = telemetryKonverter.toDTO(this)

package net.blueshell.api.telemetry.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.telemetry.persistence.Telemetry
import org.springframework.stereotype.Component

@Konverter
interface RedirectKonverter {
    fun toDTO(telemetry: Telemetry): TelemetryDTO

    fun fromDTO(dto: TelemetryDTO): Telemetry
}

@Component
class RedirectMapper : BaseMapper<Telemetry, TelemetryDTO>() {
    private val konverter = konverter<RedirectKonverter>()

    override fun fromDTO(dto: TelemetryDTO): Telemetry = konverter.fromDTO(dto)

    fun fromDTO(dto: TelemetryDTO, telemetry: Telemetry): Telemetry {
        val mapped = konverter.fromDTO(dto)
        telemetry.url = mapped.url
        telemetry.platform = mapped.platform
        dto.version?.let { telemetry.version = it }
        return telemetry
    }

    override fun toDTO(telemetry: Telemetry): TelemetryDTO = konverter.toDTO(telemetry)
}

fun Telemetry.asDTO(mapper: RedirectMapper): TelemetryDTO = mapper.toDTO(this)

fun TelemetryDTO.asEntity(mapper: RedirectMapper): Telemetry = mapper.fromDTO(this)

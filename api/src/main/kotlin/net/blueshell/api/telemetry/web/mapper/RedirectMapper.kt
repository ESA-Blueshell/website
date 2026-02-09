package net.blueshell.api.telemetry.web.mapper

import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.telemetry.persistence.Telemetry
import org.springframework.stereotype.Component

@Component
class RedirectMapper : BaseMapper<Telemetry, TelemetryDTO>() {
    override fun fromDTO(dto: TelemetryDTO): Telemetry = fromDTO(dto, Telemetry())

    fun fromDTO(dto: TelemetryDTO, telemetry: Telemetry): Telemetry {
        telemetry.url = requireNotNull(dto.url)
        telemetry.platform = requireNotNull(dto.platform)
        dto.version?.let { telemetry.version = it }
        return telemetry
    }

    override fun toDTO(telemetry: Telemetry): TelemetryDTO {
        return TelemetryDTO(
            url = telemetry.url,
            platform = telemetry.platform
        ).also { dto ->
            dto.id = telemetry.id
            dto.version = telemetry.version
        }
    }
}

fun Telemetry.asDTO(mapper: RedirectMapper): TelemetryDTO = mapper.toDTO(this)

fun TelemetryDTO.asEntity(mapper: RedirectMapper): Telemetry = mapper.fromDTO(this)

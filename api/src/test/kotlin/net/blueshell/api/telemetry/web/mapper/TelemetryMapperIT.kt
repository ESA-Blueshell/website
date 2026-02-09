package net.blueshell.api.telemetry.web.mapper

import net.blueshell.api.factory.dto.TelemetryDTOFactory
import net.blueshell.api.factory.model.TelemetryFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.telemetry.web.mapper.TelemetryMapper
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.application.TelemetryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TelemetryMapperIT @Autowired constructor(
    private val telemetryMapper: TelemetryMapper,
    private val telemetryDTOFactory: TelemetryDTOFactory,
    private val telemetryFactory: TelemetryFactory,
    private val telemetryService: TelemetryService
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted telemetry`() {
            val telemetry = persist(telemetryFactory.createBasic())

            val dto = telemetryMapper.toDTO(telemetry)

            assertThat(dto.id).isEqualTo(telemetry.id)
            assertThat(dto.url).isEqualTo(telemetry.url)
            assertThat(dto.platform).isEqualTo(telemetry.platform)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped telemetry`() {
            val dto = telemetryDTOFactory.createBasic()
            val telemetry = telemetryFactory.createBasic()

            val mapped = telemetryMapper.fromDTO(dto, telemetry)
            val saved = telemetryService.create(mapped)
            flushAndClear()

            val reloaded = reload(Telemetry::class.java, saved.id!!)

            assertThat(reloaded.platform).isEqualTo(dto.platform)
            assertThat(reloaded.url).isEqualTo(dto.url)
        }
    }
}

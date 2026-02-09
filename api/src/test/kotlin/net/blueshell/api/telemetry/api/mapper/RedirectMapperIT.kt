package net.blueshell.api.telemetry.api.mapper

import net.blueshell.api.factory.dto.TelemetryDTOFactory
import net.blueshell.api.factory.model.TelemetryFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.telemetry.api.mapper.RedirectMapper
import net.blueshell.api.telemetry.domain.model.Telemetry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RedirectMapperIT @Autowired constructor(
    private val redirectMapper: RedirectMapper,
    private val telemetryDTOFactory: TelemetryDTOFactory,
    private val telemetryFactory: TelemetryFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted telemetry`() {
            val telemetry = persist(telemetryFactory.createBasic())

            val dto = redirectMapper.toDTO(telemetry)

            assertThat(dto.id).isEqualTo(telemetry.id)
            assertThat(dto.url).isEqualTo(telemetry.url)
            assertThat(dto.platform).isEqualTo(telemetry.platform)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists telemetry mapping`() {
            val dto = telemetryDTOFactory.createBasic()
            val telemetry = telemetryFactory.createBasic()

            val mapped = redirectMapper.fromDTO(dto, telemetry)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Telemetry::class.java, saved.id!!)

            assertThat(reloaded.platform).isEqualTo(dto.platform)
            assertThat(reloaded.url).isEqualTo(dto.url)
        }
    }
}

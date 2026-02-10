package net.blueshell.api.telemetry.web.dto

import net.blueshell.api.factory.dto.TelemetryDTOFactory
import net.blueshell.api.factory.model.TelemetryFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.telemetry.application.TelemetryService
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.web.mapping.asEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TelemetryDtoIT @Autowired constructor(
    private val telemetryDTOFactory: TelemetryDTOFactory,
    private val telemetryFactory: TelemetryFactory,
    private val telemetryService: TelemetryService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists telemetry mapping`() {
            val dto = telemetryDTOFactory.createBasic()
            val telemetry = telemetryFactory.createBasic()

            val mapped = dto.asEntity(telemetry)
            val saved = telemetryService.create(mapped)
            flushAndClear()

            val reloaded = reload(Telemetry::class.java, saved.id!!)

            assertThat(reloaded.platform).isEqualTo(dto.platform)
            assertThat(reloaded.url).isEqualTo(dto.url)
        }

        @Test
        fun `persists telemetry mapping for redirect`() {
            val dto = telemetryDTOFactory.createBasic()
            val telemetry = telemetryFactory.createBasic()

            val mapped = dto.asEntity(telemetry)
            val saved = telemetryService.create(mapped)
            flushAndClear()

            val reloaded = reload(Telemetry::class.java, saved.id!!)

            assertThat(reloaded.platform).isEqualTo(dto.platform)
            assertThat(reloaded.url).isEqualTo(dto.url)
        }
    }
}

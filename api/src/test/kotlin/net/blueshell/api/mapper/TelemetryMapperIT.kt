package net.blueshell.api.mapper

import net.blueshell.api.factory.dto.TelemetryDTOFactory
import net.blueshell.api.factory.model.TelemetryFactory
import net.blueshell.api.model.Telemetry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TelemetryMapperIT @Autowired constructor(
    private val telemetryMapper: TelemetryMapper,
    private val telemetryDTOFactory: TelemetryDTOFactory,
    private val telemetryFactory: TelemetryFactory
) : MapperTestSupport() {
    @Test
    fun `persists mapped telemetry`() {
        val dto = telemetryDTOFactory.createBasic()
        val telemetry = telemetryFactory.createBasic()

        val mapped = telemetryMapper.fromDTO(dto, telemetry)
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(Telemetry::class.java, saved.id!!)
        val mappedDto = telemetryMapper.toDTO(reloaded)

        assertThat(reloaded.platform).isEqualTo(dto.platform)
        assertThat(reloaded.url).isEqualTo(dto.url)
        assertThat(mappedDto.url).isEqualTo(reloaded.url)
    }
}

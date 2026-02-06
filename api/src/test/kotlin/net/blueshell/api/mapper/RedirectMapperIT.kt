package net.blueshell.api.mapper

import net.blueshell.api.factory.dto.TelemetryDTOFactory
import net.blueshell.api.factory.model.TelemetryFactory
import net.blueshell.api.model.Telemetry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RedirectMapperIT @Autowired constructor(
    private val redirectMapper: RedirectMapper,
    private val telemetryDTOFactory: TelemetryDTOFactory,
    private val telemetryFactory: TelemetryFactory
) : MapperTestSupport() {
    @Test
    fun `persists telemetry mapping`() {
        val dto = telemetryDTOFactory.createBasic()
        val telemetry = telemetryFactory.createBasic()

        val mapped = redirectMapper.fromDTO(dto, telemetry)
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(Telemetry::class.java, saved.id!!)
        val mappedDto = redirectMapper.toDTO(reloaded)

        assertThat(reloaded.platform).isEqualTo(dto.platform)
        assertThat(reloaded.url).isEqualTo(dto.url)
        assertThat(mappedDto.url).isEqualTo(dto.url)
    }
}

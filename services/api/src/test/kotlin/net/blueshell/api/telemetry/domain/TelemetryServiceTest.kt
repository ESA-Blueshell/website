package net.blueshell.api.telemetry.domain

import jakarta.persistence.EntityManager
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.persistence.TelemetryRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class TelemetryServiceTest {
    private val manager = mock<EntityManager>()
    private val repository = mock<TelemetryRepository> { on { saveAndFlush(any<Telemetry>()) } doAnswer { it.getArgument(0) } }
    private val service =
        TelemetryService(repository).also {
            TelemetryService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(it, manager)
        }

    @Test
    fun `records a visit and reads it back`() {
        val recorded = service.createTelemetry(PlatformType.entries.first(), "/events")

        assertThat(recorded.url).isEqualTo("/events")
        verify(manager).refresh(recorded)
    }

    @Test
    fun `reads a recorded visit, and refuses one that is not there`() {
        val telemetry = Entities.telemetry()
        whenever(repository.findById(1)).thenReturn(Optional.of(telemetry))
        whenever(repository.findById(2)).thenReturn(Optional.empty())

        assertThat(service.findById(1)).isSameAs(telemetry)
        assertThatThrownBy { service.findById(2) }.isInstanceOf(TelemetryNotFoundException::class.java)
    }
}

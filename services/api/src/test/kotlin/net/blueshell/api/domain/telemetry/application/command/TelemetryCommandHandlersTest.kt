package net.blueshell.api.domain.telemetry.application.command

import net.blueshell.api.domain.telemetry.application.TelemetryService
import net.blueshell.api.domain.telemetry.command.CreateTelemetryCommand
import net.blueshell.api.domain.telemetry.command.FindTelemetryByIdCommand
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.shared.enums.PlatformType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TelemetryCommandHandlersTest {

    private val telemetryService = mock<TelemetryService>()

    @Nested
    inner class FindTelemetryById {

        private val handler = FindTelemetryByIdHandler(telemetryService)

        @Test
        fun `returns telemetry from service`() {
            val telemetry = Telemetry(PlatformType.TWITTER, "https://example.com")
            whenever(telemetryService.findById(8L)).thenReturn(telemetry)

            val result = handler.handle(FindTelemetryByIdCommand(8L))

            assertThat(result).isSameAs(telemetry)
        }
    }

    @Nested
    inner class CreateTelemetry {

        private val handler = CreateTelemetryHandler(telemetryService)

        @Test
        fun `creates telemetry with platform and url`() {
            val telemetry = Telemetry(PlatformType.TWITTER, "https://example.com/new")
            whenever(telemetryService.createTelemetry(PlatformType.TWITTER, "https://example.com/new")).thenReturn(telemetry)

            val result = handler.handle(
                CreateTelemetryCommand(
                    platform = PlatformType.TWITTER,
                    url = "https://example.com/new"
                )
            )

            assertThat(result).isSameAs(telemetry)
            verify(telemetryService).createTelemetry(eq(PlatformType.TWITTER), eq("https://example.com/new"))
        }
    }
}

package net.blueshell.api.telemetry.web

import net.blueshell.api.telemetry.persistence.TelemetryRepository
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class TelemetryControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var telemetryRepository: TelemetryRepository

    private fun createPayload(url: String = "https://example.com/${System.currentTimeMillis()}"): String =
        """{"url":"$url","platform":"TWITTER"}"""

    @Nested
    inner class FindTelemetryById {

        @Test
        fun `finds telemetry by id`() {
            val telemetry = createTelemetryFixture()

            mvc.perform(get("/telemetry/{id}", telemetry.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(telemetry.id))
                .andExpect(jsonPath("$.url").value(telemetry.url))
                .andExpect(jsonPath("$.platform").value(telemetry.platform.name))
        }

        @Test
        fun `returns not found when telemetry does not exist`() {
            mvc.perform(get("/telemetry/{id}", 999999L))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class CreateTelemetry {

        @Test
        fun `creates telemetry`() {
            val board = createUserWithRole(Role.BOARD)
            val url = "https://example.com/telemetry-${System.currentTimeMillis()}"

            val result = mvc.perform(
                post("/telemetry")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(url))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.url").value(url))
                .andExpect(jsonPath("$.platform").value("TWITTER"))
                .andReturn()

            val id = mapper.readTree(result.response.contentAsByteArray).path("id").asLong()
            val persisted = telemetryRepository.findById(id).orElseThrow()
            assertThat(persisted.url).isEqualTo(url)
            assertThat(persisted.platform).isEqualTo(PlatformType.TWITTER)
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/telemetry")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"","platform":"TWITTER"}""")
            )
                .andExpect(status().isBadRequest)
        }
    }
}

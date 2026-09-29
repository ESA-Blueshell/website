package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class GoogleCalendarClientTest {
    @Test
    fun `without a service account the client starts, and a calendar write names the Vault key to seed`() {
        val client = GoogleCalendarClient()
        ReflectionTestUtils.setField(client, "calendarId", "calendar")
        ReflectionTestUtils.setField(client, "serviceAccountJson", "")
        client.init()

        assertThatThrownBy { client.addEvent("Game night", null, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("google.calendar.serviceAccountJson")
    }
}

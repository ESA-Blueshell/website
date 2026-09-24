package net.blueshell.api.event.web

import net.blueshell.api.event.persistence.Event
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class EventGameMappingsTest {
    private val start = Instant.parse("2026-10-10T18:00:00Z")
    private val end = Instant.parse("2026-10-10T21:00:00Z")

    private val create =
        CreateEventRequest(
            committeeId = 1,
            title = "LAN",
            description = "Bring a rig.",
            startTime = start,
            endTime = end,
            approved = true,
            membersOnly = false,
            signUp = false,
        )

    private val update =
        UpdateEventRequest(
            committeeId = 1,
            title = "LAN",
            description = "Bring a rig.",
            startTime = start,
            endTime = end,
            approved = true,
            membersOnly = false,
            signUp = false,
            version = 1,
        )

    @Test
    fun `carries the games a new event names, and none where it names none`() {
        assertThat(create.copy(gameCodes = listOf("CHESS")).asData().gameCodes).containsExactly("CHESS")
        assertThat(create.asData().gameCodes).isEmpty()
    }

    @Test
    fun `carries an edit's games, and says nothing of them where the edit leaves them out`() {
        assertThat(update.copy(gameCodes = listOf("CHESS")).asData().gameCodes).containsExactly("CHESS")
        assertThat(update.asData().gameCodes).isNull()
    }

    @Test
    fun `says an event's games back in order of their codes`() {
        val event =
            Event(committee = mock(), title = "LAN", startTime = start, endTime = end).apply {
                gameCodes += "WORDLE"
                gameCodes += "CHESS"
            }
        event.id = 7
        event.createdAt = start
        event.updatedAt = start

        assertThat(event.asResponse().gameCodes).containsExactly("CHESS", "WORDLE")
    }
}

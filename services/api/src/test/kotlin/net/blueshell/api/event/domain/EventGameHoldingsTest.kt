package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class EventGameHoldingsTest {
    @Test
    fun `counts the events that name a game, and never refuses its removal`() {
        val holdings = EventGameHoldings(mock<EventRepository> { on { countNamingGame("CHESS") } doReturn 3 })

        assertThat(holdings.heldAgainst("CHESS")).isEqualTo(mapOf("events" to 3L))
        holdings.refuseRemoval("CHESS")
    }
}

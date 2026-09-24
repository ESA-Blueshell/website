package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.CommitteeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CommitteeGameHoldingsTest {
    @Test
    fun `counts the committees that organise events for a game, and never refuses its removal`() {
        val holdings = CommitteeGameHoldings(mock<CommitteeRepository> { on { countNamingGame("CHESS") } doReturn 2 })

        assertThat(holdings.heldAgainst("CHESS")).isEqualTo(mapOf("committees" to 2L))
        holdings.refuseRemoval("CHESS")
    }
}

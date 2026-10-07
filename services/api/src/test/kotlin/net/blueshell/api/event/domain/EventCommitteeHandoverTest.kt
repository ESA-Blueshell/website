package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EventCommitteeHandoverTest {
    @Test
    fun `counts the events a committee organises, and hands them all to another`() {
        val events = mock<EventRepository> { on { countOrganisedBy(4) } doReturn 3 }
        val handover = EventCommitteeHandover(events)

        assertThat(handover.countOf(4)).isEqualTo(3)
        handover.handOver(4, 7)

        verify(events).handOverCommittee(4, 7)
    }
}

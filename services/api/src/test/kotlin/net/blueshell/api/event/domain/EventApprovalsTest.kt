package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.ApprovedEvent
import net.blueshell.api.event.persistence.ApprovedEventRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.Optional

class EventApprovalsTest {
    private val repository: ApprovedEventRepository =
        mock { on { save(any<ApprovedEvent>()) } doAnswer { it.arguments[0] as ApprovedEvent } }
    private val approvals = EventApprovals(repository, JsonMapper.builder().build())

    @Test
    fun `keeps an approved event and names what changed since, field by field`() {
        val event = Entities.event(id = 6L).apply { title = "LAN" }
        whenever(repository.findById(6L)).thenReturn(Optional.empty())
        approvals.record(event)
        val kept = argumentCaptor<ApprovedEvent>()
        verify(repository).save(kept.capture())
        assertThat(kept.firstValue.id).isEqualTo(6L)
        assertThat(ApprovedEvent::class.java.getDeclaredConstructor().newInstance()).isNotNull

        whenever(repository.findById(6L)).thenReturn(Optional.of(kept.firstValue))
        assertThat(approvals.changesOf(event)).isEmpty()
        event.title = "LAN party"
        event.startTime = Instant.parse("2027-01-01T10:00:00Z")
        event.memberPrice = 5.0
        assertThat(approvals.changesOf(event)).containsExactly(EventField.TITLE, EventField.TIMES, EventField.PRICES)

        approvals.record(event)
        assertThat(approvals.changesOf(event)).isEmpty()
    }

    @Test
    fun `queues new events with no changes, and re-approvals with theirs, or none where nothing was kept`() {
        val fresh = Entities.event(id = 1L)
        val sentBack = Entities.event(id = 2L).apply { awaitingReapproval = true }
        whenever(repository.findById(2L)).thenReturn(Optional.empty())

        assertThat(approvals.queued(listOf(fresh, sentBack))).containsExactly(
            QueuedEvent(fresh, false, emptyList()),
            QueuedEvent(sentBack, true, emptyList()),
        )
    }
}

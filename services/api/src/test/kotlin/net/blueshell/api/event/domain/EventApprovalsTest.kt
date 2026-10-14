package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.ApprovedEvent
import net.blueshell.api.event.persistence.ApprovedEventRepository
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.PingedRole
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EventApprovalsTest {
    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private val kept: ApprovedEventRepository = mock()
    private val events: EventRepository = mock()
    private val approvals = EventApprovals(kept, events, JsonMapper.builder().build(), Clock.fixed(now, ZoneOffset.UTC))

    private fun snapshotOf(id: Long): ApprovedEvent {
        val fields = argumentCaptor<String>()
        verify(kept, org.mockito.kotlin.atLeastOnce()).keep(eq(id), fields.capture())
        return ApprovedEvent(id, fields.lastValue)
    }

    @Test
    fun `keeps an event each time the board approves it, the newest approval in place of the one before`() {
        val event = Entities.event(id = 6L, title = "LAN")
        approvals.record(event)
        event.title = "LAN party"
        approvals.record(event)

        verify(kept, times(2)).keep(eq(6L), org.mockito.kotlin.any())
        assertThat(snapshotOf(6L).fields).contains("LAN party")
        // JPA's no-argument constructor, which only a load from the database calls otherwise.
        assertThat(ApprovedEvent::class.java.getDeclaredConstructor().newInstance()).isNotNull()
    }

    @Test
    fun `queues the upcoming events waiting for the board, a re-approval naming every field changed since`() {
        val changed = Entities.event(id = 2L, title = "LAN")
        approvals.record(changed)
        val snapshot = snapshotOf(2L)
        changed.title = "LAN party"
        changed.pingedRoles.add(PingedRole("1", "Gamers"))
        changed.gameCodes.add("cs2")
        changed.memberPrice = 5.0
        val form = Entities.survey()
        form.replaceQuestions(listOf(Entities.question(survey = form, label = "Which day?")))
        changed.replaceSignUpForm(form)
        val fresh = Entities.event(id = 1L)
        whenever(events.findByApprovedFalseAndEndTimeGreaterThanEqualOrderByStartTimeAsc(now)).thenReturn(listOf(fresh, changed))
        whenever(kept.findAllById(listOf(1L, 2L))).thenReturn(listOf(snapshot))

        assertThat(approvals.queue()).containsExactly(
            QueuedEvent(fresh, false, emptyList()),
            QueuedEvent(
                changed,
                true,
                listOf(EventField.TITLE, EventField.PRICES, EventField.PINGED_ROLES, EventField.GAMES, EventField.SIGN_UP_FORM),
            ),
        )
        assertThat(snapshot.id).isEqualTo(2L)
    }
}

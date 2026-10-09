package net.blueshell.api.event.web

import net.blueshell.api.event.api.AnnounceChoice
import net.blueshell.api.event.api.AnnouncementLedger
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.domain.EventRoster
import net.blueshell.api.event.domain.EventUseCases
import net.blueshell.api.event.domain.Roster
import net.blueshell.api.event.domain.RosterPerson
import net.blueshell.api.shared.discord.DescriptionNodeKind
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant

class EventControllerTest {
    private val event =
        Entities.event(id = 6L).apply {
            version = 1L
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
        }
    private val useCases: EventUseCases =
        mock {
            on { approve(any(), any(), anyOrNull()) } doReturn event
            on { create(any(), anyOrNull()) } doReturn event
            on { update(any(), any(), any(), any(), anyOrNull()) } doReturn event
        }
    private val service: EventService = mock { on { findById(6L) } doReturn event }
    private val roster: EventRoster =
        mock { on { of(6L) } doReturn Roster(listOf(RosterPerson("Nelly", "https://cdn/n.png", discord = true)), guests = 2) }
    private val controller = EventController(service, useCases, AnnouncementLedger { it == 6L }, roster)

    @Test
    fun `answers an event's roster as its page shows it`() {
        val answered = controller.findEventRoster(6L)

        assertThat(answered.people.map { listOf(it.name, it.avatar, it.discord) })
            .containsExactly(listOf("Nelly", "https://cdn/n.png", true))
        assertThat(answered.guests).isEqualTo(2)
    }

    @Test
    fun `passes the board's choice on, and says of one event whether its post is out`() {
        assertThat(controller.approveEvent(6L, approved = true, announce = AnnounceChoice.NEXT_MORNING).announced).isTrue()
        verify(useCases).approve(eq(6L), eq(true), eq(AnnounceChoice.NEXT_MORNING))

        assertThat(controller.findEventById(6L).announced).isTrue()
    }

    @Test
    fun `carries one event's description as read, and none where it has no description`() {
        event.description = "**LAN** <@&223456789012345678>"

        val read = controller.findEventById(6L).descriptionTree

        assertThat(
            read
                ?.nodes
                ?.single()
                ?.children
                ?.map { it.kind },
        ).containsExactly(DescriptionNodeKind.STRONG, DescriptionNodeKind.TEXT, DescriptionNodeKind.ROLE_MENTION)
        assertThat(read?.length).isEqualTo(30)

        event.description = null
        assertThat(controller.findEventById(6L).descriptionTree).isNull()
    }

    @Test
    fun `passes a new or edited event's choice on, and says whether its post is out`() {
        val start = Instant.parse("2026-11-01T19:00:00Z")
        val created =
            CreateEventRequest(
                committeeId = 3L,
                title = "LAN",
                description = "Bring a rig.",
                startTime = start,
                endTime = start.plusSeconds(7200),
                approved = true,
                announce = AnnounceChoice.NOW,
                membersOnly = false,
                signUp = false,
            )
        val edited =
            UpdateEventRequest(
                committeeId = 3L,
                title = "LAN",
                description = "Bring a rig.",
                startTime = start,
                endTime = start.plusSeconds(7200),
                approved = true,
                announce = AnnounceChoice.NEXT_MORNING,
                membersOnly = false,
                signUp = false,
                version = 1L,
            )

        assertThat(controller.createEvent(created).announced).isTrue()
        assertThat(controller.updateEvent(6L, edited).announced).isTrue()

        verify(useCases).create(any(), eq(AnnounceChoice.NOW))
        verify(useCases).update(eq(6L), any(), eq(false), eq(1L), eq(AnnounceChoice.NEXT_MORNING))
    }
}

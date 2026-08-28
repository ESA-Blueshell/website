package net.blueshell.api.security

import net.blueshell.api.committee.domain.CommitteePermission
import net.blueshell.api.domain.event.application.permission.EventBannerPermission
import net.blueshell.api.domain.event.application.permission.EventPermission
import net.blueshell.api.domain.event.application.permission.EventSignUpPermission

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.domain.event.application.EventBannerService
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventSignUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class EventPermissionEvaluatorsTest {

    @Nested
    inner class CommitteePermissionEvaluator {
        private val service = mock<CommitteeService>()
        private val evaluator = CommitteePermission(service)

        @Test
        fun `null entity path allows read and gates write delete to board`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "write")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `entity events permission allows board or committee member`() {
            val committee = mock<Committee>()
            whenever(committee.hasMember(44L)).thenReturn(true)
            val memberAuth = guestAuth(id = 44L)

            assertThat(evaluator.hasPermission(memberAuth, committee, "events")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 99L), committee, "events")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), committee, "events")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), committee, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), committee, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), committee, "write")).isFalse()
        }

        @Test
        fun `hasPermissionId supports null-id fallback and service lookup`() {
            val committee = mock<Committee>()
            whenever(service.findById(5L)).thenReturn(committee)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermissionId(boardAuth(), 5L, "delete")).isTrue()
            verify(service).findById(5L)
        }
    }

    @Nested
    inner class EventPermissionEvaluator {
        private val service = mock<EventService>()
        private val evaluator = EventPermission(service)

        @Test
        fun `null entity path only allows signups for board`() {
            assertThat(evaluator.hasPermission(boardAuth(), null, "signups")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "signups")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isFalse()
        }

        @Test
        fun `read write delete depend on board approval or committee membership`() {
            val openToAll = eventEntity(approved = true, membersOnly = false, active = true, committeeMemberId = null)
            val memberOnly = eventEntity(approved = false, membersOnly = false, active = true, committeeMemberId = 12L)
            val privateEvent = eventEntity(approved = false, membersOnly = false, active = true, committeeMemberId = null)

            assertThat(evaluator.hasPermission(guestAuth(), openToAll, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 12L), memberOnly, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), privateEvent, "read")).isFalse()

            assertThat(evaluator.hasPermission(guestAuth(id = 12L), memberOnly, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), memberOnly, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), privateEvent, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), privateEvent, "delete")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), privateEvent, "approve")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), privateEvent, "approve")).isFalse()
        }

        @Test
        fun `signUp allows board always and otherwise enforces active approved and member role gate`() {
            val activePublic = eventEntity(approved = true, membersOnly = false, active = true, committeeMemberId = null)
            val activeMembersOnly = eventEntity(approved = true, membersOnly = true, active = true, committeeMemberId = null)
            val inactivePublic = eventEntity(approved = true, membersOnly = false, active = false, committeeMemberId = null)
            val activeUnapproved = eventEntity(approved = false, membersOnly = false, active = true, committeeMemberId = null)

            assertThat(evaluator.hasPermission(boardAuth(), inactivePublic, "signUp")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), activePublic, "signUp")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), activeMembersOnly, "signUp")).isFalse()
            assertThat(evaluator.hasPermission(memberAuth(), activeMembersOnly, "signUp")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), inactivePublic, "signUp")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), activeUnapproved, "signUp")).isFalse()
        }

        @Test
        fun `hasPermissionId requires non-null id and loads event`() {
            val event = eventEntity(approved = true, membersOnly = false, active = true, committeeMemberId = null)
            whenever(service.findById(9L)).thenReturn(event)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermissionId(boardAuth(), 9L, "delete")).isTrue()
            verify(service).findById(9L)
        }
    }

    @Nested
    inner class EventSignUpPermissionEvaluator {
        private val signUps = mock<EventSignUpService>()
        private val events = mock<EventService>()
        private val evaluator = EventSignUpPermission(signUps, events)

        @Test
        fun `denies when authentication permission or entity is missing`() {
            assertThat(evaluator.hasPermission(null, null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), mock<EventSignUp>(), null)).isFalse()
        }

        @Test
        fun `read allows board owner or committee member`() {
            val ownerSignUp = signUpEntity(signUpUserId = 5L, committeeMemberId = null, active = true, eventId = 31L)
            val committeeSignUp = signUpEntity(signUpUserId = 6L, committeeMemberId = 7L, active = true, eventId = 32L)
            val privateSignUp = signUpEntity(signUpUserId = 6L, committeeMemberId = null, active = true, eventId = 33L)

            assertThat(evaluator.hasPermission(guestAuth(id = 5L), ownerSignUp, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 7L), committeeSignUp, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 8L), privateSignUp, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), privateSignUp, "read")).isTrue()
        }

        @Test
        fun `write and delete allow board always and owner only for active events`() {
            val activeOwnerSignUp = signUpEntity(signUpUserId = 12L, committeeMemberId = null, active = true, eventId = 41L)
            val inactiveOwnerSignUp = signUpEntity(signUpUserId = 12L, committeeMemberId = null, active = false, eventId = 42L)

            assertThat(evaluator.hasPermission(boardAuth(), inactiveOwnerSignUp, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 12L), activeOwnerSignUp, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 12L), inactiveOwnerSignUp, "write")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(id = 12L), activeOwnerSignUp, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 12L), inactiveOwnerSignUp, "delete")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(id = 99L), activeOwnerSignUp, "write")).isFalse()
        }

        @Test
        fun `hasPermissionId requires non-null id and resolves sign up`() {
            val signUp = signUpEntity(signUpUserId = 2L, committeeMemberId = null, active = true, eventId = 60L)
            whenever(signUps.findById(77L)).thenReturn(signUp)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermissionId(boardAuth(), 77L, "delete")).isTrue()
            verify(signUps).findById(77L)
        }

        private fun signUpEntity(
            signUpUserId: Long?,
            committeeMemberId: Long?,
            active: Boolean,
            eventId: Long
        ): EventSignUp {
            val committee = mock<Committee>()
            if (committeeMemberId != null) {
                whenever(committee.hasMember(committeeMemberId)).thenReturn(true)
            }

            val signUpEvent = mock<Event>()
            whenever(signUpEvent.committee).thenReturn(committee)

            val activeStateEvent = mock<Event>()
            whenever(activeStateEvent.endTime).thenReturn(
                if (active) Instant.now().plusSeconds(7200) else Instant.now().minusSeconds(7200)
            )

            val signUp = mock<EventSignUp>()
            whenever(signUp.userId).thenReturn(signUpUserId)
            whenever(signUp.event).thenReturn(signUpEvent)
            whenever(signUp.eventId).thenReturn(eventId)

            whenever(events.findById(eventId)).thenReturn(activeStateEvent)
            return signUp
        }
    }

    @Nested
    inner class EventBannerPermissionEvaluator {
        private val service = mock<EventBannerService>()
        private val eventPermission = mock<EventPermission>()
        private val evaluator = EventBannerPermission(service, eventPermission)

        @Test
        fun `null entity path only allows write for committee role`() {
            assertThat(evaluator.hasPermission(committeeAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "write")).isFalse()
            assertThat(evaluator.hasPermission(committeeAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(committeeAuth(), null, "delete")).isFalse()
        }

        @Test
        fun `entity permissions delegate to event permission with expected verbs`() {
            val auth = guestAuth()
            val event = mock<Event>()
            val banner = mock<EventBanner>()
            whenever(banner.event).thenReturn(event)
            whenever(eventPermission.hasPermission(auth, event, "read")).thenReturn(true)
            whenever(eventPermission.hasPermission(auth, event, "write")).thenReturn(false)

            assertThat(evaluator.hasPermission(auth, banner, "read")).isTrue()
            assertThat(evaluator.hasPermission(auth, banner, "write")).isFalse()
            assertThat(evaluator.hasPermission(auth, banner, "delete")).isFalse()
            assertThat(evaluator.hasPermission(auth, banner, "unknown")).isFalse()

            verify(eventPermission).hasPermission(auth, event, "read")
            verify(eventPermission, times(2)).hasPermission(auth, event, "write")
        }

        @Test
        fun `hasPermissionId supports null-id fallback and id lookup`() {
            val event = mock<Event>()
            val banner = mock<EventBanner>()
            whenever(banner.event).thenReturn(event)
            whenever(service.findById(EventBanner.Id(eventId = 7L, fileId = 8L))).thenReturn(banner)
            whenever(eventPermission.hasPermission(boardAuth(), event, "write")).thenReturn(true)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "write")).isTrue()
            assertThat(
                evaluator.hasPermissionId(
                    boardAuth(),
                    EventBanner.Id(eventId = 7L, fileId = 8L),
                    "delete"
                )
            ).isTrue()

            verify(service).findById(EventBanner.Id(eventId = 7L, fileId = 8L))
            verify(eventPermission).hasPermission(boardAuth(), event, "write")
        }
    }

    private fun eventEntity(
        approved: Boolean,
        membersOnly: Boolean,
        active: Boolean,
        committeeMemberId: Long?
    ): Event {
        val committee = mock<Committee>()
        if (committeeMemberId != null) {
            whenever(committee.hasMember(committeeMemberId)).thenReturn(true)
        }

        val event = mock<Event>()
        whenever(event.committee).thenReturn(committee)
        whenever(event.approved).thenReturn(approved)
        whenever(event.membersOnly).thenReturn(membersOnly)
        whenever(event.endTime).thenReturn(
            if (active) Instant.now().plusSeconds(3600) else Instant.now().minusSeconds(3600)
        )

        return event
    }
}

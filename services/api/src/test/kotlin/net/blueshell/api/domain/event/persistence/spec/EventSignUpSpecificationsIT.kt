package net.blueshell.api.domain.event.persistence.spec

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.query.EventSignUpQuery
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.repository.EventSignUpRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.time.ZoneOffset

@SpringBootTest
class EventSignUpSpecificationsIT : UserTestSupport() {

    @Autowired
    private lateinit var eventSignUps: EventSignUpRepository

    @Nested
    inner class TimeAndFieldFilters {

        @Test
        fun `filters signups by approved false`() {
            val committee = createCommitteeFixture()
            val approvedEvent = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draftEvent = createEvent(committee, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)
            val approvedSignUp = createSignUp(approvedEvent)
            val draftSignUp = createSignUp(draftEvent)

            val result = eventSignUps.findAll(EventSignUpSpecifications.approved(false))

            assertThat(result.map { it.id }).contains(draftSignUp.id)
            assertThat(result.map { it.id }).doesNotContain(approvedSignUp.id)
        }

        @Test
        fun `filters signups by event start time range`() {
            val committee = createCommitteeFixture()
            val januaryEvent = createEvent(committee, LocalDateTime.of(2024, 1, 10, 12, 0), approved = true)
            val februaryEvent = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)

            val januarySignUp = createSignUp(januaryEvent)
            val februarySignUp = createSignUp(februaryEvent)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.timeBetween(
                    LocalDateTime.of(2024, 2, 1, 0, 0),
                    LocalDateTime.of(2024, 2, 28, 23, 59)
                )
            )

            assertThat(result.map { it.id }).contains(februarySignUp.id)
            assertThat(result.map { it.id }).doesNotContain(januarySignUp.id)
        }

        @Test
        fun `filters signups by userId and eventId in fromFilter`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val eventA = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = false)
            val eventB = createEvent(committee, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val targetUser = createUserWithRole(Role.MEMBER)
            val otherUser = createUserWithRole(Role.MEMBER)

            val target = createSignUp(eventA, targetUser)
            createSignUp(eventA, otherUser)
            createSignUp(eventB, targetUser)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.fromFilter(
                    EventSignUpQuery(userId = targetUser.id, eventId = eventA.id),
                    CurrentUser(board.id!!, setOf(Role.BOARD), board.addressId)
                )
            )

            assertThat(result.map { it.id }).contains(target.id)
            assertThat(result).hasSize(1)
        }
    }

    @Nested
    inner class FromFilterVisibility {

        @Test
        fun `non-board without committee filter sees approved signups only`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = createCommitteeFixture()
            val approvedEvent = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draftEvent = createEvent(committee, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val approvedSignUp = createSignUp(approvedEvent)
            val draftSignUp = createSignUp(draftEvent)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.fromFilter(
                    EventSignUpQuery(),
                    CurrentUser(member.id!!, setOf(Role.MEMBER), member.addressId)
                )
            )

            assertThat(result.map { it.id }).contains(approvedSignUp.id)
            assertThat(result.map { it.id }).doesNotContain(draftSignUp.id)
        }

        @Test
        fun `non-board committee member can see drafts for requested committee`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeA = createCommitteeFixture(name = "Committee A")
            val committeeB = createCommitteeFixture(name = "Committee B")
            addCommitteeMember(committeeA, member)

            val approvedA = createEvent(committeeA, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draftA = createEvent(committeeA, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)
            val draftB = createEvent(committeeB, LocalDateTime.of(2024, 2, 12, 12, 0), approved = false)

            val approvedASignUp = createSignUp(approvedA)
            val draftASignUp = createSignUp(draftA)
            val draftBSignUp = createSignUp(draftB)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.fromFilter(
                    EventSignUpQuery(committeeId = committeeA.id),
                    CurrentUser(member.id!!, setOf(Role.MEMBER), member.addressId)
                )
            )

            assertThat(result.map { it.id }).contains(approvedASignUp.id, draftASignUp.id)
            assertThat(result.map { it.id }).doesNotContain(draftBSignUp.id)
        }

        @Test
        fun `non-board non-committee-member sees only approved signups for requested committee`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = createCommitteeFixture()
            val approved = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draft = createEvent(committee, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val approvedSignUp = createSignUp(approved)
            val draftSignUp = createSignUp(draft)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.fromFilter(
                    EventSignUpQuery(committeeId = committee.id),
                    CurrentUser(member.id!!, setOf(Role.MEMBER), member.addressId)
                )
            )

            assertThat(result.map { it.id }).contains(approvedSignUp.id)
            assertThat(result.map { it.id }).doesNotContain(draftSignUp.id)
        }

        @Test
        fun `anonymous with committee filter sees only approved signups`() {
            val committee = createCommitteeFixture()
            val approved = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draft = createEvent(committee, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)
            val approvedSignUp = createSignUp(approved)
            val draftSignUp = createSignUp(draft)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.fromFilter(
                    EventSignUpQuery(committeeId = committee.id),
                    user = null
                )
            )

            assertThat(result.map { it.id }).contains(approvedSignUp.id)
            assertThat(result.map { it.id }).doesNotContain(draftSignUp.id)
        }

        @Test
        fun `board sees approved and draft signups`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val approved = createEvent(committee, LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draft = createEvent(committee, LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val approvedSignUp = createSignUp(approved)
            val draftSignUp = createSignUp(draft)

            val result = eventSignUps.findAll(
                EventSignUpSpecifications.fromFilter(
                    EventSignUpQuery(),
                    CurrentUser(board.id!!, setOf(Role.BOARD), board.addressId)
                )
            )

            assertThat(result.map { it.id }).contains(approvedSignUp.id, draftSignUp.id)
        }
    }

    private fun createEvent(committee: Committee, start: LocalDateTime, approved: Boolean): Event {
        return persist(
            Event(
                committee = committee,
                title = "Event ${System.currentTimeMillis()}",
                description = "Event description",
                location = "Campus",
                startTime = start.toInstant(ZoneOffset.UTC),
                endTime = start.plusHours(2).toInstant(ZoneOffset.UTC),
                approved = approved,
                membersOnly = false,
                signUp = true,
            )
        )
    }

    private fun createSignUp(event: Event, user: net.blueshell.api.domain.user.persistence.User = createUserWithRole(Role.MEMBER)): EventSignUp {
        return persist(
            EventSignUp(
                event = event,
                userId = user.id,
                guest = null,
            )
        )
    }
}

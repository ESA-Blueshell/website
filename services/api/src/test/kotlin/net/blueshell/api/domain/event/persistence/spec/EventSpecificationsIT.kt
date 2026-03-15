package net.blueshell.api.domain.event.persistence.spec

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@SpringBootTest
class EventSpecificationsIT : UserTestSupport() {

    @Autowired
    private lateinit var events: EventRepository

    @Nested
    inner class TimeFilters {

        @Test
        fun `filters by startTimeFrom including boundary`() {
            val committee = createCommitteeFixture()
            val before = createEvent(committee, "Before", LocalDateTime.of(2024, 2, 9, 11, 59), approved = true)
            val atBoundary = createEvent(committee, "At Boundary", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)

            val result = events.findAll(
                EventSpecifications.startTimeFrom(LocalDateTime.of(2024, 2, 10, 12, 0))
            )

            assertThat(result.map { it.id }).contains(atBoundary.id)
            assertThat(result.map { it.id }).doesNotContain(before.id)
        }

        @Test
        fun `filters by timeBetween`() {
            val committee = createCommitteeFixture()
            val january = createEvent(committee, "January Event", LocalDateTime.of(2024, 1, 10, 12, 0), approved = true)
            val february = createEvent(committee, "February Event", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val march = createEvent(committee, "March Event", LocalDateTime.of(2024, 3, 10, 12, 0), approved = true)

            val result = events.findAll(
                EventSpecifications.timeBetween(
                    LocalDateTime.of(2024, 2, 1, 0, 0),
                    LocalDateTime.of(2024, 2, 28, 23, 59)
                )
            )

            assertThat(result.map { it.id }).contains(february.id)
            assertThat(result.map { it.id }).doesNotContain(january.id, march.id)
        }

        @Test
        fun `filters by titleContains case-insensitively`() {
            val committee = createCommitteeFixture()
            val lanParty = createEvent(committee, "LAN Party Night", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val meetup = createEvent(committee, "Community Meetup", LocalDateTime.of(2024, 2, 11, 12, 0), approved = true)

            val result = events.findAll(EventSpecifications.titleContains("lan party"))

            assertThat(result.map { it.id }).contains(lanParty.id)
            assertThat(result.map { it.id }).doesNotContain(meetup.id)
        }

        @Test
        fun `filters by committeeId`() {
            val committeeA = createCommitteeFixture(name = "Committee A")
            val committeeB = createCommitteeFixture(name = "Committee B")
            val eventA = createEvent(committeeA, "Event A", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val eventB = createEvent(committeeB, "Event B", LocalDateTime.of(2024, 2, 11, 12, 0), approved = true)

            val result = events.findAll(EventSpecifications.committeeId(committeeA.id))

            assertThat(result.map { it.id }).contains(eventA.id)
            assertThat(result.map { it.id }).doesNotContain(eventB.id)
        }
    }

    @Nested
    inner class FromFilterVisibility {

        @Test
        fun `anonymous sees only approved events`() {
            val committee = createCommitteeFixture()
            val approved = createEvent(committee, "Approved", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draft = createEvent(committee, "Draft", LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val result = events.findAll(EventSpecifications.fromFilter(EventQuery(), user = null))

            assertThat(result.map { it.id }).contains(approved.id)
            assertThat(result.map { it.id }).doesNotContain(draft.id)
        }

        @Test
        fun `member sees approved and own committee drafts`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeA = createCommitteeFixture(name = "Committee A")
            val committeeB = createCommitteeFixture(name = "Committee B")
            addCommitteeMember(committeeA, member)

            val approvedB = createEvent(committeeB, "Approved B", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draftA = createEvent(committeeA, "Draft A", LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)
            val draftB = createEvent(committeeB, "Draft B", LocalDateTime.of(2024, 2, 12, 12, 0), approved = false)

            val currentUser = CurrentUser(member.id!!, setOf(Role.MEMBER), member.addressId)
            val result = events.findAll(EventSpecifications.fromFilter(EventQuery(), currentUser))

            assertThat(result.map { it.id }).contains(approvedB.id, draftA.id)
            assertThat(result.map { it.id }).doesNotContain(draftB.id)
        }

        @Test
        fun `board sees all events`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val approved = createEvent(committee, "Approved", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draft = createEvent(committee, "Draft", LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val currentUser = CurrentUser(board.id!!, setOf(Role.BOARD), board.addressId)
            val result = events.findAll(EventSpecifications.fromFilter(EventQuery(), currentUser))

            assertThat(result.map { it.id }).contains(approved.id, draft.id)
        }

        @Test
        fun `board approved false filter returns only drafts`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val approved = createEvent(committee, "Approved", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            val draft = createEvent(committee, "Draft", LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val currentUser = CurrentUser(board.id!!, setOf(Role.BOARD), board.addressId)
            val result = events.findAll(EventSpecifications.fromFilter(EventQuery(approved = false), currentUser))

            assertThat(result.map { it.id }).contains(draft.id)
            assertThat(result.map { it.id }).doesNotContain(approved.id)
        }

        @Test
        fun `anonymous approved false filter returns no events`() {
            val committee = createCommitteeFixture()
            createEvent(committee, "Approved", LocalDateTime.of(2024, 2, 10, 12, 0), approved = true)
            createEvent(committee, "Draft", LocalDateTime.of(2024, 2, 11, 12, 0), approved = false)

            val result = events.findAll(EventSpecifications.fromFilter(EventQuery(approved = false), user = null))

            assertThat(result).isEmpty()
        }
    }

    private fun createEvent(
        committee: Committee,
        title: String,
        start: LocalDateTime,
        approved: Boolean
    ): Event {
        return persist(
            Event(
                committee = committee,
                title = title,
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
}

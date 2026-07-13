package net.blueshell.api.domain.user.persistence.spec

import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class MembershipSpecificationsIT : UserTestSupport() {

    @Autowired
    private lateinit var memberships: MemberRepository

    @Nested
    inner class TimeOverlap {

        @Test
        fun `returns all memberships when both bounds are null`() {
            val first = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val second = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val third = createMembership(LocalDate.of(2024, 3, 1), null)

            val result = memberships.findAll(MembershipSpecifications.timeOverlap(null, null))

            assertThat(result.map { it.id }).contains(first.id, second.id, third.id)
        }

        @Test
        fun `filters memberships overlapping a bounded range`() {
            val january = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val february = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val ongoing = createMembership(LocalDate.of(2024, 3, 1), null)

            val result = memberships.findAll(
                MembershipSpecifications.timeOverlap(
                    LocalDate.of(2024, 2, 10),
                    LocalDate.of(2024, 3, 10)
                )
            )

            assertThat(result.map { it.id }).contains(february.id, ongoing.id)
            assertThat(result.map { it.id }).doesNotContain(january.id)
        }

        @Test
        fun `filters memberships with only upper bound by start date`() {
            val january = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val february = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val march = createMembership(LocalDate.of(2024, 3, 1), null)

            val result = memberships.findAll(
                MembershipSpecifications.timeOverlap(
                    null,
                    LocalDate.of(2024, 2, 15)
                )
            )

            assertThat(result.map { it.id }).contains(january.id, february.id)
            assertThat(result.map { it.id }).doesNotContain(march.id)
        }

        @Test
        fun `filters memberships with only lower bound by end date`() {
            val january = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val february = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val ongoing = createMembership(LocalDate.of(2024, 3, 1), null)

            val result = memberships.findAll(
                MembershipSpecifications.timeOverlap(
                    LocalDate.of(2024, 2, 10),
                    null
                )
            )

            assertThat(result.map { it.id }).contains(february.id, ongoing.id)
            assertThat(result.map { it.id }).doesNotContain(january.id)
        }

        @Test
        fun `normalizes reversed bounds`() {
            createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val february = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val ongoing = createMembership(LocalDate.of(2024, 3, 1), null)

            val reversed = memberships.findAll(
                MembershipSpecifications.timeOverlap(
                    LocalDate.of(2024, 3, 10),
                    LocalDate.of(2024, 2, 10)
                )
            )

            assertThat(reversed.map { it.id }).contains(february.id, ongoing.id)
        }
    }

    @Nested
    inner class FromQuery {

        @Test
        fun `applies time overlap from membership query`() {
            createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val february = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val ongoing = createMembership(LocalDate.of(2024, 3, 1), null)

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(
                    MembershipQuery(from = LocalDate.of(2024, 2, 10), to = LocalDate.of(2024, 3, 10)),
                    user = null
                )
            )

            assertThat(result.map { it.id }).contains(february.id, ongoing.id)
        }

        @Test
        fun `normalizes reversed bounds from membership query`() {
            createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val february = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            val ongoing = createMembership(LocalDate.of(2024, 3, 1), null)

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(
                    MembershipQuery(from = LocalDate.of(2024, 3, 10), to = LocalDate.of(2024, 2, 10)),
                    user = null
                )
            )

            assertThat(result.map { it.id }).contains(february.id, ongoing.id)
        }

        @Test
        fun `userId null returns all memberships (backward compatible)`() {
            val first = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val second = createMembership(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(MembershipQuery(), user = null)
            )

            assertThat(result.map { it.id }).contains(first.id, second.id)
        }
    }

    @Nested
    inner class UserId {

        @Test
        fun `filters to only the given user's memberships`() {
            val target = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))
            val other = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(MembershipQuery(userId = target.userId), user = null)
            )

            assertThat(result.map { it.id }).contains(target.id)
            assertThat(result.map { it.id }).doesNotContain(other.id)
        }

        @Test
        fun `userId with no memberships returns empty`() {
            val user = createUserWithRole(Role.MEMBER)

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(MembershipQuery(userId = user.id), user = null)
            )

            assertThat(result).isEmpty()
        }

        @Test
        fun `userId and date range compose with AND`() {
            val target = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            val targetUserId = target.userId
            // A second membership for the same user that falls outside the date range
            val outOfRange = createMembershipForUserId(targetUserId, LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30))

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(
                    MembershipQuery(
                        userId = targetUserId,
                        from = LocalDate.of(2024, 1, 1),
                        to = LocalDate.of(2024, 2, 28)
                    ),
                    user = null
                )
            )

            assertThat(result.map { it.id }).contains(target.id)
            assertThat(result.map { it.id }).doesNotContain(outOfRange.id)
        }

        @Test
        fun `deleted membership for the user is excluded`() {
            val membership = createMembership(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))
            val targetUserId = membership.userId

            memberships.delete(membership)

            val result = memberships.findAll(
                MembershipSpecifications.fromQuery(MembershipQuery(userId = targetUserId), user = null)
            )

            assertThat(result.map { it.id }).doesNotContain(membership.id)
        }
    }

    private fun createMembership(startDate: LocalDate, endDate: LocalDate?): Membership {
        val user = createUserWithRole(Role.MEMBER)
        return persist(
            Membership(
                user = user,
                memberType = MemberType.REGULAR,
                incasso = true,
                startDate = startDate,
                endDate = endDate,
            )
        )
    }

    private fun createMembershipForUserId(userId: Long, startDate: LocalDate, endDate: LocalDate?): Membership {
        val user = userRepository.findById(userId).orElseThrow()
        return persist(
            Membership(
                user = user,
                memberType = MemberType.REGULAR,
                incasso = true,
                startDate = startDate,
                endDate = endDate,
            )
        )
    }
}

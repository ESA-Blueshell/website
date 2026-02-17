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
    }

    private fun createMembership(startDate: LocalDate, endDate: LocalDate?): Membership {
        return persist(
            Membership().apply {
                user = createUserWithRole(Role.MEMBER)
                memberType = MemberType.REGULAR
                incasso = true
                this.startDate = startDate
                this.endDate = endDate
            }
        )
    }
}

package net.blueshell.api.contribution.domain

import jakarta.persistence.EntityManagerFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Guards the payment-email preview against N+1 regressions.
 *
 * The planner reads a period once, then one query each for paid contributions, reminders,
 * pre-notifications, erasure snapshots and any selected member holding no membership. None of
 * those is per member, so a preview of six costs what a preview of one costs — six statements
 * either way, where the per-row erasure check made it eleven.
 */
@SpringBootTest
class ContributionEmailPlannerFetchIT : UserTestSupport() {

    @Autowired
    private lateinit var planner: ContributionEmailPlanner

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private val sessionFactory: SessionFactory
        get() = entityManagerFactory.unwrap(SessionFactory::class.java)

    @Test
    fun `previewing a period does not scale queries with the number of members`() {
        val periodId = createContributionPeriodFixture().id!!
        val one = membersHoldingAMembership(1)
        val six = membersHoldingAMembership(6)

        // Prime the persistence unit so one-time, session-level statements don't skew the counts.
        planner.plan(periodId, one)
        planner.plan(periodId, six)

        val queriesForOne = countStatements { planner.plan(periodId, one) }
        val queriesForSix = countStatements { planner.plan(periodId, six) }

        assertThat(planner.plan(periodId, six).rows).hasSize(6)
        assertThat(queriesForSix)
            .describedAs("query count must not grow with the size of the selection (N+1)")
            .isEqualTo(queriesForOne)
    }

    @Test
    fun `a selection of members holding no membership costs one lookup, not one apiece`() {
        val periodId = createContributionPeriodFixture().id!!
        val one = membersHoldingNoMembership(1)
        val six = membersHoldingNoMembership(6)

        planner.plan(periodId, one)
        planner.plan(periodId, six)

        val queriesForOne = countStatements { planner.plan(periodId, one) }
        val queriesForSix = countStatements { planner.plan(periodId, six) }

        assertThat(planner.plan(periodId, six).rows).hasSize(6)
        assertThat(queriesForSix)
            .describedAs("a member without a membership must not cost their own two selects")
            .isEqualTo(queriesForOne)
    }

    private fun membersHoldingAMembership(count: Int): List<Long> =
        (1..count).map { createMembershipFixture().user.id!! }

    private fun membersHoldingNoMembership(count: Int): List<Long> =
        (1..count).map { createUserWithRole(Role.MEMBER).id!! }

    private fun countStatements(block: () -> Unit): Long {
        sessionFactory.statistics.isStatisticsEnabled = true
        sessionFactory.statistics.clear()
        block()
        return sessionFactory.statistics.prepareStatementCount
    }
}

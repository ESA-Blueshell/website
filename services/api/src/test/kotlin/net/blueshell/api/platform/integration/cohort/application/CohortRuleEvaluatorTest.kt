package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortRule
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortRuleEvaluatorTest {

    private val factCollector: UserFactCollector = mockk()
    private val rules: CohortRuleRepository = mockk()
    private val memberships: CohortMemberRepository = mockk()
    private val evaluator = CohortRuleEvaluator(factCollector, rules, memberships)

    @Test
    fun `desired set is the union of cohorts referenced by rules matching held facts`() {
        every { factCollector.collect(1L) } returns setOf(
            UserFact(CohortFactKind.ROLE, Role.MEMBER.name),
            UserFact(CohortFactKind.NEWSLETTER, "true"),
        )
        val brevoMembers = cohort(id = 10L)
        val discordMember = cohort(id = 11L)
        val newsletterList = cohort(id = 20L)
        every {
            rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.MEMBER.name)
        } returns listOf(rule(brevoMembers), rule(discordMember))
        every {
            rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.NEWSLETTER, "true")
        } returns listOf(rule(newsletterList))
        every { memberships.findAllByUserId(1L) } returns emptyList()

        val result = evaluator.evaluate(1L)

        assertThat(result.desired).containsExactlyInAnyOrder(10L, 11L, 20L)
        assertThat(result.toAdd).containsExactlyInAnyOrder(10L, 11L, 20L)
        assertThat(result.toRemove).isEmpty()
    }

    @Test
    fun `cohorts currently joined but no longer desired land in toRemove`() {
        every { factCollector.collect(1L) } returns emptySet()
        val stale = cohort(id = 99L)
        every { memberships.findAllByUserId(1L) } returns listOf(membership(stale))

        val result = evaluator.evaluate(1L)

        assertThat(result.toRemove).containsExactly(99L)
        assertThat(result.toAdd).isEmpty()
    }

    @Test
    fun `noop evaluation reports current and desired set equal`() {
        every { factCollector.collect(1L) } returns setOf(
            UserFact(CohortFactKind.ROLE, Role.MEMBER.name),
        )
        val members = cohort(id = 10L)
        every {
            rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.MEMBER.name)
        } returns listOf(rule(members))
        every { memberships.findAllByUserId(1L) } returns listOf(membership(members))

        val result = evaluator.evaluate(1L)

        assertThat(result.isNoOp).isTrue()
        assertThat(result.toAdd).isEmpty()
        assertThat(result.toRemove).isEmpty()
    }

    @Test
    fun `evaluation returns empty result when user has no facts`() {
        every { factCollector.collect(404L) } returns emptySet()
        every { memberships.findAllByUserId(404L) } returns emptyList()

        val result = evaluator.evaluate(404L)

        assertThat(result.desired).isEmpty()
        assertThat(result.current).isEmpty()
        assertThat(result.isNoOp).isTrue()
    }

    private fun cohort(id: Long): Cohort {
        val c = mockk<Cohort>()
        every { c.id } returns id
        every { c.kind } returns CohortKind.LIST
        return c
    }

    private fun rule(cohort: Cohort): CohortRule {
        val r = mockk<CohortRule>()
        every { r.cohort } returns cohort
        return r
    }

    private fun membership(cohort: Cohort): CohortMember {
        val m = mockk<CohortMember>()
        every { m.cohort } returns cohort
        return m
    }
}

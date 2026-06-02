package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortRule
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortRuleEvaluatorTest {

    private val factCollector: UserFactCollector = mockk()
    private val rules: CohortRuleRepository = mockk()
    private val memberships: CohortMemberRepository = mockk(relaxed = true)
    private val cohorts: CohortRepository = mockk()
    private val subjectRepo: net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository =
        mockk(relaxed = true)
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val users: net.blueshell.api.domain.user.application.UserService =
        mockk<net.blueshell.api.domain.user.application.UserService>(relaxed = true).also {
            every { it.isSoftDeleted(any<Long>()) } returns false
        }
    private val evaluator = CohortRuleEvaluator(factCollector, rules, memberships, cohorts, subjectRepo, jobs, users)

    @Test
    fun `cohorts in the desired set but not currently joined are added and a SyncCohortMembership job is enqueued`() {
        every { factCollector.collect(1L) } returns setOf(
            UserFact(CohortFactKind.ROLE, Role.MEMBER.name),
        )
        val members = cohort(id = 10L)
        every { rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.MEMBER.name) } returns
            listOf(rule(members))
        every { memberships.findAllByUserId(1L) } returns emptyList()
        every { cohorts.findById(10L) } returns Optional.of(members)

        val saved = slot<CohortMember>()
        every { memberships.save(capture(saved)) } answers { firstArg<CohortMember>() }

        val result = evaluator.evaluate(1L)

        assertThat(result.toAdd).containsExactly(10L)
        assertThat(result.toRemove).isEmpty()
        assertThat(saved.isCaptured).isTrue()
        assertThat(saved.captured.cohort).isSameAs(members)
        assertThat(saved.captured.userId).isEqualTo(1L)
        verify {
            jobs.enqueue(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(1L, 10L, SyncCohortMembershipIntent.ADD),
            )
        }
    }

    @Test
    fun `cohorts currently joined but no longer desired are soft-deleted and a REMOVE job is enqueued`() {
        every { factCollector.collect(1L) } returns emptySet()
        val stale = cohort(id = 99L)
        val staleMembership = membership(stale)
        every { memberships.findAllByUserId(1L) } returns listOf(staleMembership)

        val result = evaluator.evaluate(1L)

        assertThat(result.toRemove).containsExactly(99L)
        assertThat(result.toAdd).isEmpty()
        verify { memberships.delete(staleMembership) }
        verify {
            jobs.enqueue(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(1L, 99L, SyncCohortMembershipIntent.REMOVE),
            )
        }
    }

    @Test
    fun `no-op evaluation does not write or enqueue anything`() {
        every { factCollector.collect(1L) } returns setOf(
            UserFact(CohortFactKind.ROLE, Role.MEMBER.name),
        )
        val members = cohort(id = 10L)
        every { rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.MEMBER.name) } returns
            listOf(rule(members))
        every { memberships.findAllByUserId(1L) } returns listOf(membership(members))

        val result = evaluator.evaluate(1L)

        assertThat(result.isNoOp).isTrue()
        verify(exactly = 0) { memberships.save(any<CohortMember>()) }
        verify(exactly = 0) { memberships.delete(any<CohortMember>()) }
        verify(exactly = 0) { jobs.enqueue(any(), any()) }
    }

    @Test
    fun `desired set unions across all matching held facts`() {
        every { factCollector.collect(1L) } returns setOf(
            UserFact(CohortFactKind.ROLE, Role.MEMBER.name),
            UserFact(CohortFactKind.NEWSLETTER, "true"),
        )
        val brevoMembers = cohort(id = 10L)
        val newsletter = cohort(id = 20L)
        every { rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.MEMBER.name) } returns
            listOf(rule(brevoMembers))
        every { rules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.NEWSLETTER, "true") } returns
            listOf(rule(newsletter))
        every { memberships.findAllByUserId(1L) } returns emptyList()
        every { cohorts.findById(10L) } returns Optional.of(brevoMembers)
        every { cohorts.findById(20L) } returns Optional.of(newsletter)
        every { memberships.save(any<CohortMember>()) } answers { firstArg<CohortMember>() }

        val result = evaluator.evaluate(1L)

        assertThat(result.desired).containsExactlyInAnyOrder(10L, 20L)
        assertThat(result.toAdd).containsExactlyInAnyOrder(10L, 20L)
    }

    @Test
    fun `evaluation with no facts and no current memberships is a no-op`() {
        every { factCollector.collect(404L) } returns emptySet()
        every { memberships.findAllByUserId(404L) } returns emptyList()

        val result = evaluator.evaluate(404L)

        assertThat(result.desired).isEmpty()
        assertThat(result.current).isEmpty()
        assertThat(result.isNoOp).isTrue()
        verify(exactly = 0) { jobs.enqueue(any(), any()) }
    }

    private fun cohort(id: Long): Cohort {
        val c = mockk<Cohort>()
        every { c.id } returns id
        every { c.kind } returns CohortKind.LIST
        every { c.system } returns "BREVO"
        // Every cohort created after V72 has a subject; the evaluator now
        // looks it up so members can be inserted with both FKs populated.
        every { c.subjectId } returns id + 1000L
        val subject = mockk<net.blueshell.api.platform.integration.cohort.persistence.CohortSubject>(relaxed = true)
        every { subjectRepo.findById(id + 1000L) } returns java.util.Optional.of(subject)
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

package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortMembershipUpdaterTest {
    private val definitions: CohortDefinitionRegistry = mockk()
    private val cohorts: CohortRepository = mockk()
    private val targets: TargetRepository = mockk()
    private val memberships: TargetMemberRepository = mockk(relaxed = true)
    private val jobs: JobQueue = mockk(relaxed = true)
    private val updater = CohortMembershipUpdater(definitions, cohorts, targets, memberships, jobs)

    private val definition: CohortDefinition = mockk { every { key } returns "PERIOD_MEMBERS:1" }
    private val cohort: Cohort = Entities.cohort(id = 3L)
    private val target: Target = Entities.target(id = 99L, cohortId = 3L)

    @Test
    fun `queues a sync for each cohort a member joins, and for each one they leave`() {
        every { cohorts.findByDefinitionKey("PERIOD_MEMBERS:1") } returns cohort
        every { cohorts.findById(3L) } returns Optional.of(cohort)
        every { targets.findAllByCohortId(3L) } returns listOf(target)
        every { definitions.definitionsFor(7L) } returns listOf(definition)
        every { memberships.findAllByUserIdAndUserIdIsNotNull(7L) } returns emptyList()
        every { memberships.save(any<TargetMember>()) } answers { firstArg() }

        updater.updateMember(7L)

        val row = TargetMember(target = target, userId = 7L, cohort = cohort)
        every { definitions.definitionsFor(7L) } returns emptyList()
        every { memberships.findAllByUserIdAndUserIdIsNotNull(7L) } returns listOf(row)

        updater.updateMember(7L)

        verify {
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(7L, 99L, SyncCohortMembershipIntent.ADD),
                JobTrigger.MEMBERSHIP_CHANGED,
            )
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(7L, 99L, SyncCohortMembershipIntent.REMOVE),
                JobTrigger.MEMBERSHIP_CHANGED,
            )
        }
    }

    @Test
    fun `a cohort brought up to date adds who joined and removes who left`() {
        every { cohorts.findByDefinitionKey("PERIOD_MEMBERS:1") } returns cohort
        every { cohorts.findById(3L) } returns Optional.of(cohort)
        every { targets.findAllByCohortId(3L) } returns listOf(target)
        every { definitions.membersOf(definition) } returns setOf(7L)
        val leaving = TargetMember(target = target, userId = 8L, cohort = cohort)
        every { memberships.findAllByCohortIdAndUserIdIsNotNull(3L) } returns listOf(leaving)
        every { memberships.save(any<TargetMember>()) } answers { firstArg() }

        val change = updater.updateCohort(definition)

        assertThat(change.joined).containsExactly(7L)
        assertThat(change.left).containsExactly(8L)
    }

    @Test
    fun `a definition with no cohort yet changes nothing, and a target whose cohort is gone fails`() {
        every { cohorts.findByDefinitionKey("PERIOD_MEMBERS:1") } returns null
        assertThat(updater.updateCohort(definition).joined).isEmpty()

        every { cohorts.findByDefinitionKey("PERIOD_MEMBERS:1") } returns cohort
        every { cohorts.findById(3L) } returns Optional.empty()
        every { targets.findAllByCohortId(3L) } returns listOf(target)
        every { definitions.membersOf(definition) } returns setOf(7L)
        every { memberships.findAllByCohortIdAndUserIdIsNotNull(3L) } returns emptyList()

        assertThatThrownBy { updater.updateCohort(definition) }.hasMessage("Target 99 names a cohort that is not there")
    }
}

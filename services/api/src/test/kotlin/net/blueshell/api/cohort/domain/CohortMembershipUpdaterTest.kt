package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortMembershipUpdaterTest {
    private val definitions: CohortDefinitionRegistry = mockk()
    private val subjects: CohortSubjectRepository = mockk()
    private val cohorts: CohortRepository = mockk()
    private val memberships: CohortMemberRepository = mockk(relaxed = true)
    private val jobs: JobQueue = mockk(relaxed = true)
    private val updater = CohortMembershipUpdater(definitions, subjects, cohorts, memberships, jobs)

    private val definition: CohortDefinition = mockk { every { key } returns "PERIOD_MEMBERS:1" }
    private val subject: CohortSubject = mockk { every { id } returns 3L }
    private val cohort: Cohort =
        mockk {
            every { id } returns 99L
            every { subjectId } returns 3L
        }

    @Test
    fun `queues a sync for each cohort a member joins, and for each one they leave`() {
        every { subjects.findByDefinitionKey("PERIOD_MEMBERS:1") } returns subject
        every { subjects.findById(3L) } returns Optional.of(subject)
        every { cohorts.findAllBySubjectId(3L) } returns listOf(cohort)
        every { definitions.definitionsFor(7L) } returns listOf(definition)
        every { memberships.findAllByUserIdAndUserIdIsNotNull(7L) } returns emptyList()
        every { memberships.save(any<CohortMember>()) } answers { firstArg() }

        updater.updateMember(7L)

        val row: CohortMember =
            mockk {
                every { userId } returns 7L
                every { this@mockk.cohort } returns this@CohortMembershipUpdaterTest.cohort
                every { this@mockk.subject } returns this@CohortMembershipUpdaterTest.subject
            }
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
}

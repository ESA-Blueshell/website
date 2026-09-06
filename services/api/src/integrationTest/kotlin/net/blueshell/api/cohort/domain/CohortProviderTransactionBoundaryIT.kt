package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.jobs.domain.JobHandlerRegistry
import net.blueshell.api.platform.integration.mock.MockTargetStrategy
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper

/**
 * Proves the no-provider-call-inside-a-DB-transaction rule on the job
 * path. [AbstractJsonJobHandler.handle][net.blueshell.api.jobs.api.AbstractJsonJobHandler]
 * is `@Transactional`, so a job is always dispatched with a transaction
 * active. The application services suspend it (`PROPAGATION_NOT_SUPPORTED`)
 * around every [TargetStrategy][net.blueshell.api.cohort.domain.TargetStrategy]
 * call; [MockTargetStrategy] records whether a transaction was actually active
 * at each call so we can assert it never is.
 */
@SpringBootTest
class CohortProviderTransactionBoundaryIT : UserTestSupport() {

    // Through the registry rather than the bean: a job type nothing handles fails here too.
    @Autowired private lateinit var handlers: JobHandlerRegistry

    @Autowired private lateinit var cohorts: CohortRepository

    @Autowired private lateinit var subjects: CohortSubjectRepository

    @Autowired private lateinit var externalIds: ExternalIdMappingRepository

    @Autowired private lateinit var port: MockTargetStrategy

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `membership-sync ADD calls the provider outside any transaction`() {
        val user = createUserWithRole(Role.MEMBER)
        val cohort = newCohort(newSubject())
        externalIds.saveAndFlush(ExternalIdMapping(USER_AGGREGATE, user.id!!, TargetSystem.BREVO.name, "ext-user"))
        port.transactionActiveDuringCalls.clear()

        handler(CohortJobs.SyncCohortMembership.type).handle(
            objectMapper.writeValueAsString(
                CohortJobs.SyncCohortMembershipPayload(user.id!!, cohort.id!!, SyncCohortMembershipIntent.ADD),
            ),
            executionId = null,
        )

        assertThat(port.transactionActiveDuringCalls).isNotEmpty.containsOnly(false)
    }

    @Test
    fun `reconcile-list verify fetches the member list outside any transaction`() {
        val cohort = newCohort(newSubject())
        port.transactionActiveDuringCalls.clear()

        handler(CohortJobs.ReconcileList.type).handle(
            objectMapper.writeValueAsString(CohortJobs.ReconcileListPayload(cohort.id!!)),
            executionId = null,
        )

        assertThat(port.transactionActiveDuringCalls).isNotEmpty.containsOnly(false)
    }

    private fun handler(jobType: String) =
        requireNotNull(handlers.get(jobType)) { "No handler registered for $jobType" }

    private fun newSubject(): CohortSubject =
        subjects.save(CohortSubject(type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newCohort(subject: CohortSubject): Cohort =
        cohorts.save(
            Cohort(
                system = TargetSystem.BREVO.name,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
                externalId = "ext-cohort",
            ),
        )
}

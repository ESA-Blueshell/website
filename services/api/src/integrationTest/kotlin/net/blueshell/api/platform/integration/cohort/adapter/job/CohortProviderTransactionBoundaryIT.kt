package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.mock.MockCohortPort
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
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
 * around every [CohortPort][net.blueshell.api.platform.integration.cohort.port.out.CohortPort]
 * call; [MockCohortPort] records whether a transaction was actually active
 * at each call so we can assert it never is.
 */
@SpringBootTest
class CohortProviderTransactionBoundaryIT : UserTestSupport() {

    @Autowired private lateinit var syncHandler: SyncCohortMembershipJobHandler

    @Autowired private lateinit var verifyHandler: ReconcileListJobHandler

    @Autowired private lateinit var cohorts: CohortRepository

    @Autowired private lateinit var subjects: CohortSubjectRepository

    @Autowired private lateinit var externalIds: ExternalIdMappingRepository

    @Autowired private lateinit var port: MockCohortPort

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `membership-sync ADD calls the provider outside any transaction`() {
        val user = createUserWithRole(Role.MEMBER)
        val cohort = newCohort(newSubject())
        externalIds.saveAndFlush(ExternalIdMapping(USER_AGGREGATE, user.id!!, TargetSystem.BREVO.name, "ext-user"))
        port.transactionActiveDuringCalls.clear()

        syncHandler.handle(
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

        verifyHandler.handle(
            objectMapper.writeValueAsString(CohortJobs.ReconcileListPayload(cohort.id!!)),
            executionId = null,
        )

        assertThat(port.transactionActiveDuringCalls).isNotEmpty.containsOnly(false)
    }

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

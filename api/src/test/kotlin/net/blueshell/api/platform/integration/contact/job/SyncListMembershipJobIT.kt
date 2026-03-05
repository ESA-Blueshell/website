package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.application.job.SyncListMembershipJob
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class SyncListMembershipJobIT : UserTestSupport() {

    @Autowired
    private lateinit var syncListMembershipJob: SyncListMembershipJob

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @Autowired
    private lateinit var periods: ContributionPeriodService

    @Autowired
    private lateinit var contactListRepository: ContactListRepository

    @Autowired
    private lateinit var contactRepository: ContactRepository

    @Autowired
    private lateinit var contactListMembershipRepository: ContactListMembershipRepository

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `creates ContactList lazily when period has no contactListId`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        assertThat(period.contactListId).isNull()

        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )

        assertThat(contactListRepository.findAll()).hasSize(1)

        val refreshedPeriod = periods.findById(period.id!!)
        assertThat(refreshedPeriod.contactListId)
            .describedAs("Period contactListId should be assigned after lazy creation")
            .isNotNull()
    }

    @Test
    fun `reuses existing ContactList`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        // Run once to create list
        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )
        assertThat(contactListRepository.findAll()).hasSize(1)

        // Run again — should reuse, not create a second list
        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )
        assertThat(contactListRepository.findAll())
            .describedAs("Should reuse existing ContactList, not create a second one")
            .hasSize(1)
    }

    @Test
    fun `creates Contact and membership when user has contribution`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )

        // Contact DB record is created synchronously by contactSyncService.syncContact()
        val record = contactRepository.findByUserId(user.id!!)
        assertThat(record).describedAs("Contact should be created for user").isNotNull()

        // DB membership is created synchronously
        val contactList = contactListRepository.findAll().single()
        val membership = contactListMembershipRepository
            .findByContactIdAndContactListId(record!!.id!!, contactList.id!!)
        assertThat(membership).describedAs("Membership should be created").isNotNull()
    }

    @Test
    fun `dispatches AddToList job when user has contribution`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )

        // AddToList job should be dispatched (auto-dispatch enabled — wait for it to complete)
        awaitJobSuccess(ContactJobs.AddToList.type)

        val contactId = mockContactAdapter.getAllContacts().keys.single()
        val externalListId = mockContactAdapter.getAllLists().keys.single()
        assertThat(mockContactAdapter.isInList(contactId, externalListId))
            .describedAs("Contact should be in external list after AddToList job")
            .isTrue()
    }

    @Test
    fun `removes membership when user has no contribution`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        // First: add to list
        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )
        val record = contactRepository.findByUserId(user.id!!)!!
        val contactList = contactListRepository.findAll().single()
        assertThat(
            contactListMembershipRepository.findByContactIdAndContactListId(record.id!!, contactList.id!!)
        ).isNotNull()

        // Remove contribution then sync again
        transactionTemplate.executeWithoutResult {
            val contributions = entityManager.createQuery(
                "FROM Contribution c WHERE c.id.userId = :userId AND c.id.contributionPeriodId = :periodId",
                Contribution::class.java
            ).setParameter("userId", user.id!!)
                .setParameter("periodId", period.id!!)
                .resultList
            contributions.forEach { entityManager.remove(entityManager.merge(it)) }
        }

        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )

        assertThat(
            contactListMembershipRepository.findByContactIdAndContactListId(record.id!!, contactList.id!!)
        ).describedAs("Membership should be soft-deleted after contribution removed").isNull()
    }

    @Test
    fun `no-op remove when user has no Contact and no contribution`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        // No contribution created

        // Should not throw
        syncListMembershipJob.handle(
            objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!))
        )

        assertThat(contactRepository.findByUserId(user.id!!)).isNull()
    }

    private fun createContribution(user: User, period: ContributionPeriod): Contribution =
        persist(Contribution(user = user, contributionPeriod = period))

    private fun awaitJobSuccess(
        jobType: String,
        expectedCount: Int = 1,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val successCount = findJobsByType(jobType).count { it.status == JobExecutionStatus.SUCCESS }
            if (successCount >= expectedCount) return
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        val successCount = executions.count { it.status == JobExecutionStatus.SUCCESS }
        assertThat(successCount)
            .describedAs("Expected $expectedCount successful $jobType jobs, but found $successCount")
            .isGreaterThanOrEqualTo(expectedCount)
    }
}

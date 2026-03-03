package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SyncListMembershipJobIT : UserTestSupport() {

    @Autowired
    private lateinit var syncListMembershipJob: SyncListMembershipJob

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @Autowired
    private lateinit var users: UserService

    @Autowired
    private lateinit var periods: ContributionPeriodService

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `creates period list lazily when period has no listId`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        assertThat(period.listId).isNull()

        val payload = objectMapper.writeValueAsString(
            ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!)
        )

        syncListMembershipJob.handle(payload)

        val lists = mockContactAdapter.getAllLists()
        assertThat(lists)
            .describedAs("List should be lazily created for period")
            .hasSize(1)

        val refreshedPeriod = periods.findById(period.id!!)
        assertThat(refreshedPeriod.listId)
            .describedAs("Period listId should be assigned after lazy creation")
            .isNotNull()
    }

    @Test
    fun `reuses existing period list`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        // Pre-create list and assign to period
        val existingListId = mockContactAdapter.createList("Existing List", "contributionPeriods")
        transactionTemplate.executeWithoutResult {
            val p = periods.findById(period.id!!)
            p.listId = existingListId.toLong()
            entityManager.merge(p)
            entityManager.flush()
        }

        val payload = objectMapper.writeValueAsString(
            ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!)
        )

        syncListMembershipJob.handle(payload)

        assertThat(mockContactAdapter.getAllLists())
            .describedAs("Should reuse existing list, not create a second one")
            .hasSize(1)
    }

    @Test
    fun `syncs contact when user has no contactId`() {
        val user = createUserWithRole(Role.MEMBER)
        assertThat(user.contactId).isNull()

        val period = createContributionPeriodFixture()
        createContribution(user, period)

        val payload = objectMapper.writeValueAsString(
            ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!)
        )

        syncListMembershipJob.handle(payload)

        val refreshedUser = users.findById(user.id!!)
        assertThat(refreshedUser.contactId)
            .describedAs("User contactId should be assigned after on-demand sync")
            .isNotNull()

        val lists = mockContactAdapter.getAllLists()
        assertThat(lists.values.single().contactIds)
            .describedAs("User should be added to the period list")
            .hasSize(1)
    }

    @Test
    fun `skips removal when user has no contactId and no contribution`() {
        val user = createUserWithRole(Role.MEMBER)
        assertThat(user.contactId).isNull()

        val period = createContributionPeriodFixture()
        // No contribution created — user should be removed from list, but has no contactId

        // Pre-create list
        val listId = mockContactAdapter.createList("Test List", "contributionPeriods")
        transactionTemplate.executeWithoutResult {
            val p = periods.findById(period.id!!)
            p.listId = listId.toLong()
            entityManager.merge(p)
            entityManager.flush()
        }

        val payload = objectMapper.writeValueAsString(
            ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!)
        )

        // Should not throw
        syncListMembershipJob.handle(payload)

        assertThat(mockContactAdapter.getAllLists().values.single().contactIds)
            .describedAs("List should remain empty since user has no contactId")
            .isEmpty()
    }

    private fun createContribution(user: User, period: ContributionPeriod): Contribution {
        val contribution = Contribution(
            user = user,
            contributionPeriod = period
        )
        return persist(contribution)
    }
}

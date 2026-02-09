package net.blueshell.api.contribution.application

import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.platform.integration.contact.job.AddContactToListJobHandler
import net.blueshell.api.platform.integration.contact.job.CreateContributionPeriodListJobHandler
import net.blueshell.api.platform.integration.contact.job.RemoveContactFromListJobHandler
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionEventIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var contributions: ContributionService

    @Autowired
    private lateinit var periods: ContributionPeriodService

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var contributionFactory: ContributionFactory

    @Autowired
    private lateinit var periodFactory: ContributionPeriodFactory

    @Test
    fun `publishes add contact event on contribution create`() {
        val user = persist(userFactory.createBasic())
        val period = periods.create(
            periodFactory.createWithCustomizations { it.listId = 123L }
        )
        val contribution = contributionFactory.createWithCustomizations {
            it.user = user
            it.contributionPeriod = period
        }

        contributions.create(contribution)

        assertTrue(jobExecutions.findByJobType(AddContactToListJobHandler.JOB_TYPE).isNotEmpty())
    }

    @Test
    fun `publishes remove contact event on contribution delete`() {
        val user = persist(userFactory.createBasic())
        val period = periods.create(
            periodFactory.createWithCustomizations { it.listId = 123L }
        )
        val contribution = contributionFactory.createWithCustomizations {
            it.user = user
            it.contributionPeriod = period
        }
        val saved = contributions.create(contribution)

        contributions.delete(saved)

        assertTrue(jobExecutions.findByJobType(RemoveContactFromListJobHandler.JOB_TYPE).isNotEmpty())
    }

    @Test
    fun `publishes list creation event when period has no list id`() {
        val period = periodFactory.createWithCustomizations { it.listId = null }

        val saved = periods.create(period)

        assertTrue(
            jobExecutions.findByJobType(CreateContributionPeriodListJobHandler.JOB_TYPE).isNotEmpty()
        )
    }
}

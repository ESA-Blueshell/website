package net.blueshell.api.contribution.application

import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.platform.integration.contact.job.AddContactToListJob
import net.blueshell.api.platform.integration.contact.job.RemoveContactFromListJob
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionServiceIT : EventIntegrationTestSupport() {

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

    @Nested
    inner class Create {

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

            assertTrue(jobExecutions.findByJobType(AddContactToListJob.TYPE).isNotEmpty())
        }
    }

    @Nested
    inner class Delete {

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

            assertTrue(jobExecutions.findByJobType(RemoveContactFromListJob.TYPE).isNotEmpty())
        }
    }
}

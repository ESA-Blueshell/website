package net.blueshell.api.contribution.application

import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.platform.integration.queue.ContactJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionServiceIT : ServiceTestSupport() {

    @Autowired
    private lateinit var contributions: net.blueshell.api.domain.contribution.application.ContributionService

    @Autowired
    private lateinit var periods: net.blueshell.api.domain.contribution.application.ContributionPeriodService

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

            assertTrue(jobExecutions.findByJobType(ContactJobs.AddToList.type).isNotEmpty())
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

            assertTrue(jobExecutions.findByJobType(ContactJobs.RemoveFromList.type).isNotEmpty())
        }
    }
}

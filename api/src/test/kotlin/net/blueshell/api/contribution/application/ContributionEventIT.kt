package net.blueshell.api.contribution.application

import net.blueshell.api.contribution.application.event.ContributionChangedEvent
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.platform.integration.event.job.AddContactToListEvent
import net.blueshell.api.platform.integration.event.job.CreateContributionPeriodListEvent
import net.blueshell.api.platform.integration.event.job.RemoveContactFromListEvent
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

        assertTrue(applicationEvents.stream(ContributionChangedEvent::class.java).anyMatch { it.userId == user.id })
        assertTrue(applicationEvents.stream(AddContactToListEvent::class.java).anyMatch { it.userId == user.id })
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

        assertTrue(applicationEvents.stream(RemoveContactFromListEvent::class.java).anyMatch { it.userId == user.id })
    }

    @Test
    fun `publishes list creation event when period has no list id`() {
        val period = periodFactory.createWithCustomizations { it.listId = null }

        val saved = periods.create(period)

        assertTrue(
            applicationEvents.stream(CreateContributionPeriodListEvent::class.java)
                .anyMatch { it.periodId == saved.id }
        )
    }
}

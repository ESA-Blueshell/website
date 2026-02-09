package net.blueshell.api.contribution.application

import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.platform.integration.queue.ContactJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionPeriodServiceIT : ServiceTestSupport() {

    @Autowired
    private lateinit var periods: ContributionPeriodService

    @Autowired
    private lateinit var periodFactory: ContributionPeriodFactory

    @Nested
    inner class Create {

        @Test
        fun `publishes list creation event when period has no list id`() {
            val period = periodFactory.createWithCustomizations { it.listId = null }

            periods.create(period)

            assertTrue(jobExecutions.findByJobType(ContactJobs.CreateContributionPeriodList.type).isNotEmpty())
        }
    }
}

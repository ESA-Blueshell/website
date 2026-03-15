package net.blueshell.api.domain.contribution.application.listener

import net.blueshell.api.domain.contribution.application.event.ContributionChange
import net.blueshell.api.domain.contribution.application.event.ContributionChanged
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionContactListenerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var listener: ContributionContactListener

    @Test
    fun `dispatches SyncListMembership on ContributionChanged`() {
        val event = ContributionChanged(userId = 1L, periodId = 2L, changeType = ContributionChange.CREATED)

        listener.onContributionChange(event)

        val jobs = findJobsByType(ContactJobs.ProcessListMembership.type)
        assertThat(jobs)
            .describedAs("Should schedule one SyncListMembership job")
            .hasSize(1)

        val payload = jobs.first().payload
        assertThat(payload)
            .contains("\"userId\":1")
            .contains("\"periodId\":2")
    }

    @Test
    fun `dispatches for all change types`() {
        ContributionChange.entries.forEachIndexed { index, changeType ->
            val event = ContributionChanged(
                userId = (10 + index).toLong(),
                periodId = (20 + index).toLong(),
                changeType = changeType
            )

            listener.onContributionChange(event)
        }

        val jobs = findJobsByType(ContactJobs.ProcessListMembership.type)
        assertThat(jobs)
            .describedAs("Should schedule one job per change type")
            .hasSize(ContributionChange.entries.size)
    }
}

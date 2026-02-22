package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component

/**
 * Job handler for creating contribution period lists in external contact system.
 *
 * Uses ContactSyncAdapter (ADR-019 ACL) to isolate from specific contact provider.
 */
@Component
class CreateContributionPeriodListJob(
    objectMapper: ObjectMapper,
    private val contactAdapter: ContactSyncAdapter,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<ContactJobs.CreateContributionPeriodListPayload>(
    objectMapper,
    ContactJobs.CreateContributionPeriodList.payloadType
) {
    override val jobType: String = ContactJobs.CreateContributionPeriodList.type

    override fun handlePayload(payload: ContactJobs.CreateContributionPeriodListPayload) {
        val period = periods.findById(payload.periodId)
        if (period.listId != null) return

        // Create list name from period dates
        val listName = String.format(
            "Contribution Paid %d - %d",
            period.startDate.year,
            period.endDate.year
        )

        // Create the list in external system
        val listId = contactAdapter.createList(listName, "contributionPeriods")
        periods.updateListId(period.id!!, listId.toLong())
    }
}

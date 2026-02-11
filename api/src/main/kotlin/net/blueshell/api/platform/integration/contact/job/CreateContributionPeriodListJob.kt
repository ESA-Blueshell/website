package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.ContactJobs
import org.springframework.stereotype.Component

@Component
class CreateContributionPeriodListJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<ContactJobs.CreateContributionPeriodListPayload>(
    objectMapper,
    ContactJobs.CreateContributionPeriodList.payloadType
) {
    override val jobType: String = ContactJobs.CreateContributionPeriodList.type

    override fun handlePayload(payload: ContactJobs.CreateContributionPeriodListPayload) {
        val period = periods.findById(payload.periodId)
        if (period.listId != null) return
        val listId = contacts.createList(period)
        periods.updateListId(period.id!!, listId)
    }
}

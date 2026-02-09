package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import org.springframework.stereotype.Component

@Component
class CreateContributionPeriodListJobHandler(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<CreateContributionPeriodListPayload>(
    objectMapper,
    CreateContributionPeriodListPayload::class.java
) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: CreateContributionPeriodListPayload) {
        val period = periods.findById(payload.periodId)
        if (period.listId != null) return
        val listId = contacts.createList(period)
        periods.updateListId(period.id!!, listId)
    }

    companion object {
        const val JOB_TYPE = "contact.create-period-list"
    }
}

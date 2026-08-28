package net.blueshell.api.domain.contribution.application

import net.blueshell.api.jobs.web.JobSubjectResolver
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
@Order(40)
class ContributionPeriodJobSubjectResolver(
    private val periods: ContributionPeriodService,
) : JobSubjectResolver {
    override val payloadFields = listOf("contributionPeriodId", "periodId")
    override val entityType = "CONTRIBUTION_PERIOD"

    override fun label(id: Long): String {
        val period = try {
            periods.findById(id)
        } catch (error: ResponseStatusException) {
            if (error.statusCode == HttpStatus.NOT_FOUND) null else throw error
        } ?: return "Contribution period #$id"
        return "Contribution period #$id (${period.startDate} - ${period.endDate})"
    }
}

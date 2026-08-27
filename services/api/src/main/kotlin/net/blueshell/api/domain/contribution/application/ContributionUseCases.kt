package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.application.result.ContributionResult
import net.blueshell.api.domain.contribution.application.result.toResult
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.UserService
import org.springframework.stereotype.Service

/** Recording a contribution resolves both the user and the period first. */
@Service
class ContributionUseCases(
    private val service: ContributionService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService,
) {
    fun create(userId: Long, contributionPeriodId: Long): ContributionResult =
        service.create(
            Contribution(
                user = users.findById(userId),
                contributionPeriod = contributionPeriods.findById(contributionPeriodId),
            ),
        ).toResult()
}

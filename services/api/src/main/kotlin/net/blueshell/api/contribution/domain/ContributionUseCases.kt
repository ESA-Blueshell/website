package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.UserService
import org.springframework.stereotype.Service
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService

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

package net.blueshell.api.service.contribution

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.repository.contribution.ContributionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionService @Autowired constructor(
    repository: ContributionRepository,
    private val periodService: ContributionPeriodService
) : BaseModelService<Contribution, ContributionRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution> {
        val contributionPeriod = periodService.findById(contributionPeriodId)
        return repository.findByContributionPeriod(contributionPeriod)
    }
}

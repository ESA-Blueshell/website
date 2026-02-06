package net.blueshell.api.service.contribution

import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.repository.contribution.ContributionRepository
import net.blueshell.api.service.base.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionService @Autowired constructor(
    repository: ContributionRepository,
    private val periodService: ContributionPeriodService
) : BaseModelService<Contribution, Contribution.Id, ContributionRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution> {
        periodService.findById(contributionPeriodId)
        return repository.findById_ContributionPeriodId(contributionPeriodId)
    }
}

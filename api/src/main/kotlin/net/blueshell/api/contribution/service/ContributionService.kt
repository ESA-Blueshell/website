package net.blueshell.api.contribution.service

import net.blueshell.api.contribution.model.Contribution
import net.blueshell.api.contribution.repository.ContributionRepository
import net.blueshell.api.shared.service.BaseModelService
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

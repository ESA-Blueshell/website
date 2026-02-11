package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.application.event.ContributionChange
import net.blueshell.api.domain.contribution.application.event.ContributionChanged
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.repository.ContributionRepository
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionService @Autowired constructor(
    repository: ContributionRepository,
    private val periodService: ContributionPeriodService,
    private val events: AfterCommitEventPublisher
) : BaseModelService<Contribution, Contribution.Id, ContributionRepository>(repository) {
    @Transactional
    override fun create(entity: Contribution): Contribution {
        val saved = super.create(entity)
        publishChange(saved, ContributionChange.CREATED)
        return saved
    }

    @Transactional
    override fun update(entity: Contribution): Contribution {
        val saved = super.update(entity)
        publishChange(saved, ContributionChange.UPDATED)
        return saved
    }

    @Transactional
    override fun delete(entity: Contribution) {
        val userId = entity.userId
        val periodId = entity.contributionPeriodId
        super.delete(entity)
        events.publish(
            ContributionChanged(
                userId,
                periodId,
                ContributionChange.DELETED
            )
        )
    }

    @Transactional
    override fun deleteById(id: Contribution.Id) {
        val contribution = findById(id)
        super.deleteById(id)
        publishChange(contribution, ContributionChange.DELETED)
    }

    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution> {
        periodService.findById(contributionPeriodId)
        return repository.findByIdContributionPeriodId(contributionPeriodId)
    }

    private fun publishChange(contribution: Contribution, changeType: ContributionChange) {
        events.publish(
            ContributionChanged(
                contribution.userId,
                contribution.contributionPeriodId,
                changeType
            )
        )
    }
}

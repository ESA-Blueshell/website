package net.blueshell.api.contribution.application

import net.blueshell.api.contribution.application.event.ContributionChangeType
import net.blueshell.api.contribution.application.event.ContributionChangedEvent
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionRepository
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
        publishChange(saved, ContributionChangeType.CREATED)
        return saved
    }

    @Transactional
    override fun update(entity: Contribution): Contribution {
        val saved = super.update(entity)
        publishChange(saved, ContributionChangeType.UPDATED)
        return saved
    }

    @Transactional
    override fun delete(entity: Contribution) {
        val userId = entity.userId
        val periodId = entity.contributionPeriodId
        super.delete(entity)
        events.publish(ContributionChangedEvent(userId, periodId, ContributionChangeType.DELETED))
    }

    @Transactional
    override fun deleteById(id: Contribution.Id) {
        val contribution = findById(id)
        super.deleteById(id)
        publishChange(contribution, ContributionChangeType.DELETED)
    }

    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution> {
        periodService.findById(contributionPeriodId)
        return repository.findById_ContributionPeriodId(contributionPeriodId)
    }

    private fun publishChange(contribution: Contribution, changeType: ContributionChangeType) {
        events.publish(
            ContributionChangedEvent(
                contribution.userId,
                contribution.contributionPeriodId,
                changeType
            )
        )
    }
}

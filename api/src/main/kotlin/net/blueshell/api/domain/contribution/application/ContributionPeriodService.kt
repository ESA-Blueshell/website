package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.application.event.ContributionPeriodChanged
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionPeriodRepository
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionPeriodService @Autowired constructor(
    repository: ContributionPeriodRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<ContributionPeriod, Long, ContributionPeriodRepository>(repository) {
    @Transactional
    override fun create(entity: ContributionPeriod): ContributionPeriod {
        val saved = super.create(entity)
        events.publish(
            ContributionPeriodChanged(
                saved.id!!
            )
        )
        return saved
    }

    @Transactional
    override fun update(entity: ContributionPeriod): ContributionPeriod {
        val saved = super.update(entity)
        events.publish(
            ContributionPeriodChanged(
                saved.id!!
            )
        )
        return saved
    }

    @Transactional(readOnly = true)
    fun findLatest(): ContributionPeriod {
        return repository.findCurrentOrLatestContributionPeriod()
    }

    @Transactional
    fun updateListId(periodId: Long, listId: Long) {
        val period = findById(periodId)
        period.listId = listId
        update(period)
    }
}

package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.application.event.ContributionPeriodChanged
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.repository.ContributionPeriodRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionPeriodService @Autowired constructor(
    repository: ContributionPeriodRepository,
    private val trackedEvents: TrackedEventPublisher
) : BaseModelService<ContributionPeriod, Long, ContributionPeriodRepository>(repository) {
    @Transactional
    override fun create(entity: ContributionPeriod): ContributionPeriod {
        val saved = super.create(entity)
        trackedEvents.publish { actor ->
            ContributionPeriodChanged(
                saved.id!!,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun update(entity: ContributionPeriod): ContributionPeriod {
        val saved = super.update(entity)
        trackedEvents.publish { actor ->
            ContributionPeriodChanged(
                saved.id!!,
                actor = actor
            )
        }
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

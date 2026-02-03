package net.blueshell.api.service.contribution

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.api.repository.contribution.ContributionPeriodRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionPeriodService @Autowired constructor(
    repository: ContributionPeriodRepository,
    events: ApplicationEventPublisher
) : BaseModelService<ContributionPeriod, ContributionPeriodRepository>(repository) {
    @Transactional(readOnly = true)
    fun findLatest(): ContributionPeriod {
        return repository!!.findCurrentOrLatestContributionPeriod()
    }

    @Transactional
    fun updateListId(periodId: Long, listId: Long) {
        val period = findById(periodId)
        period.listId = listId
        update(period)
    }
}

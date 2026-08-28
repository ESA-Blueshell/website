package net.blueshell.api.contribution.api

import net.blueshell.api.contribution.domain.ContributionChange
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.user.api.UserService
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionService @Autowired constructor(
    repository: ContributionRepository,
    private val periodService: ContributionPeriodService,
    private val users: UserService,
    private val trackedEvents: TrackedEventPublisher
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
        trackedEvents.publish { actor ->
            ContributionChanged(
                userId,
                periodId,
                ContributionChange.DELETED,
                actor = actor
            )
        }
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

    @Transactional(readOnly = true)
    fun existsByUserIdAndPeriodId(userId: Long, periodId: Long): Boolean {
        return repository.existsById(Contribution.Id(userId, periodId))
    }

    @Transactional
    fun ensurePaid(userId: Long, periodId: Long): Boolean {
        if (existsByUserIdAndPeriodId(userId, periodId)) return false
        try {
            create(
                Contribution(
                    user = users.findById(userId),
                    contributionPeriod = periodService.findById(periodId),
                ),
            )
        } catch (ex: DataIntegrityViolationException) {
            if (existsByUserIdAndPeriodId(userId, periodId)) return false
            throw ex
        }
        return true
    }

    private fun publishChange(contribution: Contribution, changeType: ContributionChange) {
        trackedEvents.publish { actor ->
            ContributionChanged(
                contribution.userId,
                contribution.contributionPeriodId,
                changeType,
                actor = actor
            )
        }
    }
}

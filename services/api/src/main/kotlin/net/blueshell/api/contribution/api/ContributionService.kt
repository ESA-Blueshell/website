package net.blueshell.api.contribution.api

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.contribution.domain.ContributionChange
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ContributionService
    @Autowired
    constructor(
        private val repository: ContributionRepository,
        private val periodService: ContributionPeriodService,
        private val users: UserService,
        private val trackedEvents: TrackedEventPublisher,
    ) {
        // Read back after each write, so the columns the database fills are on the answer.
        @PersistenceContext
        private lateinit var em: EntityManager

        private fun written(row: Contribution): Contribution = repository.saveAndFlush(row).also(em::refresh)

        // The existence query flushes the session first, which writes what the edit cascades (a new
        // address on a user, say) before the merge; merging it unwritten fails on the lazy owner.
        private fun rewritten(row: Contribution): Contribution {
            val id = row.id
            if (!repository.existsById(id)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Contribution not found with id: $id")
            }
            return written(row)
        }

        @Transactional(readOnly = true)
        fun findById(id: Contribution.Id): Contribution =
            repository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Contribution not found with id: $id")
            }

        @Transactional
        fun create(entity: Contribution): Contribution {
            val saved = written(entity)
            publishChange(saved, ContributionChange.CREATED)
            return saved
        }

        @Transactional
        fun update(entity: Contribution): Contribution {
            val saved = rewritten(entity)
            publishChange(saved, ContributionChange.UPDATED)
            return saved
        }

        @Transactional
        fun delete(entity: Contribution) {
            val userId = entity.userId
            val periodId = entity.contributionPeriodId
            repository.delete(entity)
            trackedEvents.publish { actor ->
                ContributionChanged(
                    userId,
                    periodId,
                    ContributionChange.DELETED,
                    actor = actor,
                )
            }
        }

        @Transactional
        fun deleteById(id: Contribution.Id) {
            val contribution = findById(id)
            repository.delete(contribution)
            publishChange(contribution, ContributionChange.DELETED)
        }

        @Transactional(readOnly = true)
        fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution> {
            periodService.findById(contributionPeriodId)
            return repository.findByIdContributionPeriodId(contributionPeriodId)
        }

        @Transactional(readOnly = true)
        fun existsByUserIdAndPeriodId(
            userId: Long,
            periodId: Long,
        ): Boolean = repository.existsById(Contribution.Id(userId, periodId))

        @Transactional
        fun ensurePaid(
            userId: Long,
            periodId: Long,
        ): Boolean {
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

        private fun publishChange(
            contribution: Contribution,
            changeType: ContributionChange,
        ) {
            trackedEvents.publish { actor ->
                ContributionChanged(
                    contribution.userId,
                    contribution.contributionPeriodId,
                    changeType,
                    actor = actor,
                )
            }
        }
    }

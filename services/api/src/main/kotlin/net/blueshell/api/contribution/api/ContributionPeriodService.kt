package net.blueshell.api.contribution.api

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.contribution.domain.ContributionPeriodChanged
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionPeriodRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ContributionPeriodService
    @Autowired
    constructor(
        private val repository: ContributionPeriodRepository,
        private val trackedEvents: TrackedEventPublisher,
    ) {
        // Read back after each write, so the columns the database fills are on the answer.
        @PersistenceContext
        private lateinit var em: EntityManager

        private fun written(row: ContributionPeriod): ContributionPeriod = repository.saveAndFlush(row).also(em::refresh)

        // The existence query flushes the session first, which writes what the edit cascades (a new
        // address on a user, say) before the merge; merging it unwritten fails on the lazy owner.
        private fun rewritten(row: ContributionPeriod): ContributionPeriod {
            val id = row.id
            if (id == null || !repository.existsById(id)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "ContributionPeriod not found with id: $id")
            }
            return written(row)
        }

        @Transactional(readOnly = true)
        fun findById(id: Long): ContributionPeriod =
            repository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ContributionPeriod not found with id: $id")
            }

        @Transactional(readOnly = true)
        fun findAll(): List<ContributionPeriod> = repository.findAll()

        @Transactional(readOnly = true)
        fun existsById(id: Long): Boolean = repository.existsById(id)

        @Transactional
        fun deleteById(id: Long) = repository.delete(findById(id))

        @Transactional
        fun create(entity: ContributionPeriod): ContributionPeriod {
            val saved = written(entity)
            trackedEvents.publish { actor ->
                ContributionPeriodChanged(
                    saved.id!!,
                    actor = actor,
                )
            }
            return saved
        }

        @Transactional
        fun update(entity: ContributionPeriod): ContributionPeriod {
            val saved = rewritten(entity)
            trackedEvents.publish { actor ->
                ContributionPeriodChanged(
                    saved.id!!,
                    actor = actor,
                )
            }
            return saved
        }

        @Transactional(readOnly = true)
        fun findLatest(): ContributionPeriod? = repository.findCurrentOrLatestContributionPeriod()

        @Transactional
        fun updateContactListId(
            periodId: Long,
            contactListId: Long,
        ) {
            val period = findById(periodId)
            period.contactListId = contactListId
            update(period)
        }
    }

package net.blueshell.api.contribution.domain

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ContributionReminderService
    @Autowired
    constructor(
        private val repository: ContributionReminderRepository,
        private val periodService: ContributionPeriodService,
        private val jobs: JobQueue,
    ) {
        // Read back after each write, so the columns the database fills are on the answer.
        @PersistenceContext
        private lateinit var em: EntityManager

        private fun written(row: ContributionReminder): ContributionReminder = repository.saveAndFlush(row).also(em::refresh)

        @Transactional(readOnly = true)
        fun findById(id: Long): ContributionReminder =
            repository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ContributionReminder not found with id: $id")
            }

        @Transactional
        fun create(reminder: ContributionReminder): ContributionReminder = written(reminder)

        @Transactional(readOnly = true)
        fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder> {
            periodService.findById(contributionPeriodId)
            return repository.findByContributionPeriod_Id(contributionPeriodId)
        }

        /**
         * Writes the ask and queues its email, in that order and in one transaction. The job
         * carries the ask's own id, so a repeat ask sends the email it wrote rather than an
         * earlier one, and the dispatcher holds the send until this transaction commits.
         */
        @Transactional
        fun record(reminder: ContributionReminder): ContributionReminder {
            val written = create(reminder)
            jobs.runAsync(
                ContributionJobs.ContributionReminder,
                ContributionJobs.ContributionReminderPayload(requireNotNull(written.id)),
                JobTrigger.SITE_ACTION,
            )
            return written
        }

        @Transactional
        fun recordAll(reminders: List<ContributionReminder>): List<ContributionReminder> = reminders.map { record(it) }
    }

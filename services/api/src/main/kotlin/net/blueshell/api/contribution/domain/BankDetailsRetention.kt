package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.MandateRetention
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

/**
 * Wipes a mandate's sealed bank details once nobody can dispute a debit under it any more. Under
 * SEPA that is 13 months after the last collection: the latest debit date of a notification under
 * the mandate in a run submitted to ING. It only judges mandates no longer collected from, so one
 * in use is never wiped, and one that was never collected from is wiped as soon as collecting stops.
 * A run whose file may be in ING already, not yet marked as submitted and with its date still
 * ahead, holds the wipe off, so bank details never go while a debit under them is on its way.
 */
@Service
class BankDetailsRetention(
    private val mandates: MandateRetention,
    private val notifications: IncassoNotificationRepository,
    private val jobs: JobQueue,
    private val clock: Clock,
) {
    @Scheduled(cron = $$"${privacy.bank-details-wipe-cron:0 30 4 * * *}")
    fun queueDailyWipe() {
        jobs.runAsync(ContributionJobs.WipeBankDetails, ContributionJobs.WipeBankDetailsPayload(), JobTrigger.SCHEDULED_RUN)
    }

    /** Wipes every stopped mandate whose 13 months are over, and answers how many it wiped. */
    fun wipeExpired(): Int {
        val today = LocalDate.now(clock)
        return mandates.stopped(today).count { stopped ->
            val last = notifications.lastCollectionDate(stopped.userId, stopped.reference)
            val disputable = last != null && last.plusMonths(DISPUTE_MONTHS).isAfter(today)
            val onItsWay = notifications.countPendingCollections(stopped.userId, stopped.reference, today) > 0
            !disputable && !onItsWay && mandates.wipe(stopped, today)
        }
    }

    private companion object {
        const val DISPUTE_MONTHS = 13L
    }
}

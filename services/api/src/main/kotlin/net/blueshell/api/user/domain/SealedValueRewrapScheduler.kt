package net.blueshell.api.user.domain

import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.UserJobs
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Queues the nightly rewrap, which moves sealed values onto the newest version of their key after a rotation. */
@Component
class SealedValueRewrapScheduler(
    private val jobs: JobQueue,
) {
    @Scheduled(cron = $$"${privacy.rewrap-cron:0 0 4 * * *}")
    fun rewrapSealedValues() {
        jobs.runAsync(UserJobs.RewrapSealedValues, UserJobs.RewrapSealedValuesPayload(), JobTrigger.SCHEDULED_RUN)
    }
}

package net.blueshell.api.vote.domain

import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Queues the reveal every few minutes, so a vote is revealed shortly after its date without anybody
 * pressing anything. The job's dedup key drops a run while one is waiting, and a second pod that
 * reveals a vote anyway fails on the vote's version, so nothing is revealed twice.
 */
@Component
class VoteRevealScheduler(
    private val jobs: JobQueue,
) {
    @Scheduled(cron = $$"${app.vote.reveal-cron:0 */5 * * * *}")
    fun queueReveal() {
        jobs.runAsync(VoteJobs.RevealDue, VoteJobs.RevealDuePayload(), JobTrigger.SCHEDULED_RUN)
    }
}

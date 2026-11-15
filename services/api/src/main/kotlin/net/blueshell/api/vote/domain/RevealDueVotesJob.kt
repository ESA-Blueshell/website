package net.blueshell.api.vote.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.JobTally
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Counts and reveals the annual votes whose reveal date has passed. */
@Component
class RevealDueVotesJob(
    objectMapper: ObjectMapper,
    private val votes: AnnualVotes,
) : AbstractJsonJobHandler<VoteJobs.RevealDuePayload>(objectMapper, VoteJobs.RevealDue) {
    override fun handlePayload(payload: VoteJobs.RevealDuePayload) {
        val revealed = votes.revealDue()
        if (revealed == 0) return skip("No vote is due")
        counted(JobTally.VOTES_REVEALED, revealed.toLong())
    }
}

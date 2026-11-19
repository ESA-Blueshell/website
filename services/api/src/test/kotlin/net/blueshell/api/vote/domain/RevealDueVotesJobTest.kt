package net.blueshell.api.vote.domain

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.shared.job.JobTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class RevealDueVotesJobTest {
    @Test
    fun `reveals what is due and counts it, and skips a run with nothing due`() {
        val votes = mock<VoteUseCases>()
        whenever(votes.revealDue()).thenReturn(2, 0)
        val mapper = JsonMapper.builder().build()
        val job = RevealDueVotesJob(mapper, votes)

        val revealed = job.handle(mapper.writeValueAsString(VoteJobs.RevealDuePayload()), 5, forced = false)
        val none = job.handle(mapper.writeValueAsString(VoteJobs.RevealDuePayload()), 6, forced = false)

        assertThat((revealed as JobOutcome.Done).counts).containsExactly(JobCount(JobTally.VOTES_REVEALED, 2))
        assertThat(none).isInstanceOf(JobOutcome.Skipped::class.java)
    }

    @Test
    fun `the job is the vote's own type`() {
        assertThat(VoteJobs.RevealDue.type).isEqualTo("vote.reveal-due")
        assertThat(VoteJobs.RevealDue.payloadType).isEqualTo(VoteJobs.RevealDuePayload::class.java)
    }

    @Test
    fun `the scheduler queues the reveal as a scheduled run`() {
        val jobs = mock<JobQueue>()

        VoteRevealScheduler(jobs).queueReveal()

        verify(jobs).runAsync(VoteJobs.RevealDue, VoteJobs.RevealDuePayload(), JobTrigger.SCHEDULED_RUN)
    }
}

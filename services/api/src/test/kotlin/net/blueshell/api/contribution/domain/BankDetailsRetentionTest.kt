package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.MandateRetention
import net.blueshell.api.user.api.StoppedMandate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BankDetailsRetentionTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val clock = Clock.fixed(Instant.parse("2026-10-03T04:30:00Z"), ZoneOffset.UTC)
    private val mandates: MandateRetention = mock()
    private val notifications: IncassoNotificationRepository = mock()
    private val jobs: JobQueue = mock()
    private val retention = BankDetailsRetention(mandates, notifications, jobs, clock)

    private fun stopped(
        membershipId: Long,
        lastCollection: LocalDate?,
    ) = StoppedMandate(membershipId, membershipId + 100, "BLUESHELL-$membershipId").also {
        whenever(notifications.lastCollectionDate(it.userId, it.reference)).thenReturn(lastCollection)
    }

    @Test
    fun `wipes a stopped mandate 13 months after its last collection, and one never collected from at once`() {
        val thirteenMonthsAgo = stopped(1, LocalDate.of(2025, 9, 3))
        val dayShort = stopped(2, LocalDate.of(2025, 9, 4))
        val never = stopped(3, null)
        val changedInBetween = stopped(4, null)
        whenever(mandates.stopped(today)).thenReturn(listOf(thirteenMonthsAgo, dayShort, never, changedInBetween))
        whenever(mandates.wipe(1)).thenReturn(true)
        whenever(mandates.wipe(3)).thenReturn(true)
        whenever(mandates.wipe(4)).thenReturn(false)

        assertThat(retention.wipeExpired()).isEqualTo(2)

        verify(mandates).wipe(1)
        verify(mandates, never()).wipe(2)
        verify(mandates).wipe(3)
    }

    @Test
    fun `the daily run is queued as a scheduled run, and is skipped when nothing is due`() {
        retention.queueDailyWipe()
        verify(jobs).runAsync(ContributionJobs.WipeBankDetails, ContributionJobs.WipeBankDetailsPayload(), JobTrigger.SCHEDULED_RUN)

        val mapper = JsonMapper.builder().build()
        val job = WipeBankDetailsJob(mapper, retention)
        val payload = mapper.writeValueAsString(ContributionJobs.WipeBankDetailsPayload())
        val never = stopped(3, null)
        whenever(mandates.stopped(today)).thenReturn(listOf(never), emptyList())
        whenever(mandates.wipe(3)).thenReturn(true)

        assertThat(job.handle(payload, 5, forced = false)).isInstanceOf(JobOutcome.Done::class.java)
        assertThat(job.handle(payload, 6, forced = false)).isInstanceOf(JobOutcome.Skipped::class.java)
    }
}

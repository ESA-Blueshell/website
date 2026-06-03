package net.blueshell.api.platform.integration.cohort.application.ledger

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CohortLedgerTest {

    private val members: CohortMemberRepository = mockk(relaxed = true)
    private val ledger = CohortLedger(members)

    init {
        every { members.save(any<CohortMember>()) } answers { firstArg() }
    }
    private val cohort: Cohort = mockk { every { id } returns 99L }
    private val subject: CohortSubject = mockk()
    private val now: LocalDateTime = LocalDateTime.parse("2026-06-01T10:00:00")

    @Test
    fun `markPushed stamps syncedAt and external id on the desired row`() {
        val row = member(userId = 1L)
        every { members.findByCohortIdAndUserId(99L, 1L) } returns row

        val stamped = ledger.markPushed(99L, 1L, "ext-1", now)

        assertThat(stamped).isTrue()
        assertThat(row.syncedAt).isEqualTo(now)
        assertThat(row.externalUserId).isEqualTo("ext-1")
        verify { members.save(row) }
    }

    @Test
    fun `markPushed reports false when the desired row is gone`() {
        every { members.findByCohortIdAndUserId(99L, 1L) } returns null

        assertThat(ledger.markPushed(99L, 1L, "ext-1", now)).isFalse()
        verify(exactly = 0) { members.save(any<CohortMember>()) }
    }

    @Test
    fun `markVerified sets verifiedAt and backfills syncedAt when absent`() {
        val row = member(userId = 1L)

        ledger.markVerified(row, "ext-1", "Ada", now)

        assertThat(row.verifiedAt).isEqualTo(now)
        assertThat(row.syncedAt).isEqualTo(now)
        assertThat(row.label).isEqualTo("Ada")
    }

    @Test
    fun `markVerified keeps an earlier syncedAt`() {
        val pushedAt = now.minusHours(1)
        val row = member(userId = 1L).apply { syncedAt = pushedAt }

        ledger.markVerified(row, "ext-1", null, now)

        assertThat(row.syncedAt).isEqualTo(pushedAt)
        assertThat(row.verifiedAt).isEqualTo(now)
    }

    @Test
    fun `markDrifted clears both stamps`() {
        val row = member(userId = 1L).apply {
            syncedAt = now
            verifiedAt = now
        }

        ledger.markDrifted(row)

        assertThat(row.syncedAt).isNull()
        assertThat(row.verifiedAt).isNull()
    }

    @Test
    fun `foldStrangerIntoDesired moves external state and drops the stranger`() {
        val stranger = member(userId = null).apply {
            externalUserId = "ext-7"
            verifiedAt = now
            label = "Linked"
        }
        val desired = member(userId = 7L)

        ledger.foldStrangerIntoDesired(desired, stranger)

        assertThat(desired.externalUserId).isEqualTo("ext-7")
        assertThat(desired.syncedAt).isEqualTo(now)
        assertThat(desired.verifiedAt).isEqualTo(now)
        assertThat(desired.label).isEqualTo("Linked")
        verify { members.delete(stranger) }
    }

    private fun member(userId: Long?): CohortMember =
        CohortMember(cohort = cohort, userId = userId, subject = subject)
}

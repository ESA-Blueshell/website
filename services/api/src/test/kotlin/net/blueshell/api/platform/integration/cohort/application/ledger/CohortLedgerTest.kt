package net.blueshell.api.platform.integration.cohort.application.ledger

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import io.mockk.slot
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortMemberState
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.state
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns emptyList()

        val stamped = ledger.markPushed(99L, 1L, "ext-1", now)

        assertThat(stamped).isTrue()
        assertThat(row.syncedAt).isEqualTo(now)
        assertThat(row.externalUserId).isEqualTo("ext-1")
        assertThat(row.state).isEqualTo(CohortMemberState.SYNCED)
        verify { members.save(row) }
    }

    @Test
    fun `markPushed claims a matching stranger before stamping the desired row`() {
        val row = member(userId = 1L)
        val stranger = member(userId = null).apply {
            externalUserId = "ext-1"
            verifiedAt = now.minusHours(1)
        }
        every { members.findByCohortIdAndUserId(99L, 1L) } returns row
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns listOf(stranger)

        val stamped = ledger.markPushed(99L, 1L, "ext-1", now)

        assertThat(stamped).isTrue()
        verifySequence {
            members.findByCohortIdAndUserId(99L, 1L)
            members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1"))
            members.delete(stranger)
            members.flush()
            members.save(row)
        }
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
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns emptyList()

        ledger.markVerified(row, "ext-1", "Ada", now)

        assertThat(row.verifiedAt).isEqualTo(now)
        assertThat(row.syncedAt).isEqualTo(now)
        assertThat(row.label).isEqualTo("Ada")
        assertThat(row.state).isEqualTo(CohortMemberState.VERIFIED)
    }

    @Test
    fun `markVerified keeps an earlier syncedAt`() {
        val pushedAt = now.minusHours(1)
        val row = member(userId = 1L).apply { syncedAt = pushedAt }
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns emptyList()

        ledger.markVerified(row, "ext-1", null, now)

        assertThat(row.syncedAt).isEqualTo(pushedAt)
        assertThat(row.verifiedAt).isEqualTo(now)
    }

    @Test
    fun `markVerified claims a matching stranger before stamping the desired row`() {
        val row = member(userId = 1L)
        val stranger = member(userId = null).apply {
            externalUserId = "ext-1"
            verifiedAt = now.minusHours(1)
        }
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns listOf(stranger)

        ledger.markVerified(row, "ext-1", "Ada", now)

        verifySequence {
            members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1"))
            members.delete(stranger)
            members.flush()
            members.save(row)
        }
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
        assertThat(row.state).isEqualTo(CohortMemberState.DESIRED)
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
        assertThat(desired.state).isEqualTo(CohortMemberState.VERIFIED)
        verify { members.delete(stranger) }
    }

    @Test
    fun `foldStrangerIntoDesired soft deletes and flushes the stranger before saving desired`() {
        val stranger = member(userId = null).apply {
            externalUserId = "ext-7"
            verifiedAt = now
            label = "Linked"
        }
        val desired = member(userId = 7L)

        ledger.foldStrangerIntoDesired(desired, stranger)

        verifySequence {
            members.delete(stranger)
            members.flush()
            members.save(desired)
        }
    }

    @Test
    fun `upsertStranger inserts a STRANGER row`() {
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNull(99L, "ext-9") } returns null
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-9") } returns null
        val saved = slot<CohortMember>()
        every { members.save(capture(saved)) } answers { firstArg() }

        ledger.upsertStranger(cohort, subject, "ext-9", "Stranger", now)

        assertThat(saved.captured.state).isEqualTo(CohortMemberState.STRANGER)
        assertThat(saved.captured.externalUserId).isEqualTo("ext-9")
    }

    @Test
    fun `upsertStranger refuses to insert when a desired row already owns the external id`() {
        val desired = member(userId = 9L).apply { externalUserId = "ext-9" }
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNull(99L, "ext-9") } returns null
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-9") } returns desired

        ledger.upsertStranger(cohort, subject, "ext-9", "Desired", now)

        verify(exactly = 0) { members.save(any<CohortMember>()) }
    }

    @Test
    fun `upsertStranger rejects a blank external id`() {
        assertThatThrownBy { ledger.upsertStranger(cohort, subject, "  ", null, now) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { members.save(any<CohortMember>()) }
    }

    @Test
    fun `upsertStranger rejects an empty external id`() {
        assertThatThrownBy { ledger.upsertStranger(cohort, subject, "", null, now) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { members.save(any<CohortMember>()) }
    }

    private fun member(userId: Long?): CohortMember =
        CohortMember(cohort = cohort, userId = userId, subject = subject)
}

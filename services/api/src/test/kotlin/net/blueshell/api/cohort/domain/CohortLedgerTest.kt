package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CohortLedgerTest {
    private val members: TargetMemberRepository = mockk(relaxed = true)
    private val ledger = CohortLedger(members)

    init {
        every { members.save(any<TargetMember>()) } answers { firstArg() }
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(any(), any()) } returns null
    }

    private val target: Target = Entities.target(id = 99L)
    private val cohort: Cohort = Entities.cohort()
    private val now: LocalDateTime = LocalDateTime.parse("2026-06-01T10:00:00")

    @Test
    fun `markPushed stamps syncedAt and external id on the desired row`() {
        val row = member(userId = 1L)
        every { members.findByTargetIdAndUserId(99L, 1L) } returns row
        every { members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns emptyList()

        val stamped = ledger.markPushed(99L, 1L, "ext-1", now)

        assertThat(stamped).isTrue()
        assertThat(row.syncedAt).isEqualTo(now)
        assertThat(row.externalUserId).isEqualTo("ext-1")
        assertThat(row.state).isEqualTo(TargetMemberState.SYNCED)
        verify { members.save(row) }
    }

    @Test
    fun `markPushed claims a matching stranger before stamping the desired row`() {
        val row = member(userId = 1L)
        val stranger =
            member(userId = null).apply {
                externalUserId = "ext-1"
                verifiedAt = now.minusHours(1)
            }
        every { members.findByTargetIdAndUserId(99L, 1L) } returns row
        every { members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns listOf(stranger)

        val stamped = ledger.markPushed(99L, 1L, "ext-1", now)

        assertThat(stamped).isTrue()
        verifySequence {
            members.findByTargetIdAndUserId(99L, 1L)
            members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-1")
            members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1"))
            members.delete(stranger)
            members.flush()
            members.save(row)
        }
    }

    @Test
    fun `markPushed refuses external id owned by another desired row`() {
        val row = member(userId = 1L)
        val owner = member(userId = 2L).apply { externalUserId = "ext-1" }
        every { members.findByTargetIdAndUserId(99L, 1L) } returns row
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-1") } returns owner

        assertThatThrownBy { ledger.markPushed(99L, 1L, "ext-1", now) }
            .isInstanceOf(ExternalIdAlreadyOwnedException::class.java)
            .hasMessageContaining("cohort 99")
            .hasMessageContaining("ext-1")
            .hasMessageContaining("user 2")

        assertThat(row.externalUserId).isNull()
        assertThat(row.syncedAt).isNull()
        verify(exactly = 0) { members.save(any<TargetMember>()) }
    }

    @Test
    fun `markPushed reports false when the desired row is gone`() {
        every { members.findByTargetIdAndUserId(99L, 1L) } returns null

        assertThat(ledger.markPushed(99L, 1L, "ext-1", now)).isFalse()
        verify(exactly = 0) { members.save(any<TargetMember>()) }
    }

    @Test
    fun `markVerified sets verifiedAt and backfills syncedAt when absent`() {
        val row = member(userId = 1L)
        every { members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns emptyList()

        ledger.markVerified(row, "ext-1", "Ada", now)

        assertThat(row.verifiedAt).isEqualTo(now)
        assertThat(row.syncedAt).isEqualTo(now)
        assertThat(row.label).isEqualTo("Ada")
        assertThat(row.state).isEqualTo(TargetMemberState.VERIFIED)
    }

    @Test
    fun `markVerified keeps an earlier syncedAt`() {
        val pushedAt = now.minusHours(1)
        val row = member(userId = 1L).apply { syncedAt = pushedAt }
        every { members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns emptyList()

        ledger.markVerified(row, "ext-1", null, now)

        assertThat(row.syncedAt).isEqualTo(pushedAt)
        assertThat(row.verifiedAt).isEqualTo(now)
    }

    @Test
    fun `markVerified claims a matching stranger before stamping the desired row`() {
        val row = member(userId = 1L)
        val stranger =
            member(userId = null).apply {
                externalUserId = "ext-1"
                verifiedAt = now.minusHours(1)
            }
        every { members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1")) } returns listOf(stranger)

        ledger.markVerified(row, "ext-1", "Ada", now)

        verifySequence {
            members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-1")
            members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1"))
            members.delete(stranger)
            members.flush()
            members.save(row)
        }
    }

    @Test
    fun `markDrifted clears both stamps`() {
        val row =
            member(userId = 1L).apply {
                syncedAt = now
                verifiedAt = now
            }

        ledger.markDrifted(row)

        assertThat(row.syncedAt).isNull()
        assertThat(row.verifiedAt).isNull()
        assertThat(row.state).isEqualTo(TargetMemberState.DESIRED)
    }

    @Test
    fun `foldStrangerIntoDesired moves external state and drops the stranger`() {
        val stranger =
            member(userId = null).apply {
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
        assertThat(desired.state).isEqualTo(TargetMemberState.VERIFIED)
        verify { members.delete(stranger) }
    }

    @Test
    fun `foldStrangerIntoDesired soft deletes and flushes the stranger before saving desired`() {
        val stranger =
            member(userId = null).apply {
                externalUserId = "ext-7"
                verifiedAt = now
                label = "Linked"
            }
        val desired = member(userId = 7L)

        ledger.foldStrangerIntoDesired(desired, stranger)

        verifySequence {
            members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-7")
            members.delete(stranger)
            members.flush()
            members.save(desired)
        }
    }

    @Test
    fun `foldStrangerIntoDesired refuses external id owned by another desired row`() {
        val stranger =
            member(userId = null).apply {
                externalUserId = "ext-7"
                verifiedAt = now
                label = "Linked"
            }
        val desired = member(userId = 7L)
        val owner = member(userId = 8L).apply { externalUserId = "ext-7" }
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-7") } returns owner

        assertThatThrownBy { ledger.foldStrangerIntoDesired(desired, stranger) }
            .isInstanceOf(ExternalIdAlreadyOwnedException::class.java)
            .hasMessageContaining("cohort 99")
            .hasMessageContaining("ext-7")
            .hasMessageContaining("user 8")

        assertThat(desired.externalUserId).isNull()
        assertThat(desired.syncedAt).isNull()
        assertThat(desired.verifiedAt).isNull()
        verify(exactly = 0) { members.delete(stranger) }
        verify(exactly = 0) { members.save(any<TargetMember>()) }
    }

    @Test
    fun `upsertStranger inserts a STRANGER row`() {
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNull(99L, "ext-9") } returns null
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-9") } returns null
        val saved = slot<TargetMember>()
        every { members.save(capture(saved)) } answers { firstArg() }

        ledger.upsertStranger(target, cohort, "ext-9", "Stranger", now)

        assertThat(saved.captured.state).isEqualTo(TargetMemberState.STRANGER)
        assertThat(saved.captured.externalUserId).isEqualTo("ext-9")
    }

    @Test
    fun `upsertStranger refuses to insert when a desired row already owns the external id`() {
        val desired = member(userId = 9L).apply { externalUserId = "ext-9" }
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNull(99L, "ext-9") } returns null
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-9") } returns desired

        ledger.upsertStranger(target, cohort, "ext-9", "Desired", now)

        verify(exactly = 0) { members.save(any<TargetMember>()) }
    }

    @Test
    fun `upsertStranger rejects a blank external id`() {
        assertThatThrownBy { ledger.upsertStranger(target, cohort, "  ", null, now) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { members.save(any<TargetMember>()) }
    }

    @Test
    fun `upsertStranger rejects an empty external id`() {
        assertThatThrownBy { ledger.upsertStranger(target, cohort, "", null, now) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { members.save(any<TargetMember>()) }
    }

    private fun member(userId: Long?): TargetMember = TargetMember(target = target, userId = userId, cohort = cohort)
}

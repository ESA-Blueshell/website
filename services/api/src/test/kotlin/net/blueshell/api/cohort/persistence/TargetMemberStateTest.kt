package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TargetMemberStateTest {
    private val target: Target = Entities.target(id = 1L)
    private val cohort: Cohort = Entities.cohort()
    private val at: LocalDateTime = LocalDateTime.parse("2026-06-01T10:00:00")

    @Test
    fun `desired row — user, no stamps`() {
        assertThat(member(userId = 7L).state).isEqualTo(TargetMemberState.DESIRED)
    }

    @Test
    fun `synced row — user with syncedAt only`() {
        val row =
            member(userId = 7L).apply {
                externalUserId = "ext"
                syncedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.SYNCED)
    }

    @Test
    fun `verified row — user with both stamps`() {
        val row =
            member(userId = 7L).apply {
                externalUserId = "ext"
                syncedAt = at
                verifiedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.VERIFIED)
    }

    @Test
    fun `stranger row — no user, external id, verified`() {
        val row =
            member(userId = null).apply {
                externalUserId = "ext"
                verifiedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.STRANGER)
    }

    @Test
    fun `invalid — no user and no external id`() {
        val row = member(userId = null).apply { verifiedAt = at }
        assertThat(row.state).isEqualTo(TargetMemberState.INVALID)
    }

    @Test
    fun `invalid — no user, external id, but not verified`() {
        val row = member(userId = null).apply { externalUserId = "ext" }
        assertThat(row.state).isEqualTo(TargetMemberState.INVALID)
    }

    @Test
    fun `invalid — blank external id stranger`() {
        val row =
            member(userId = null).apply {
                externalUserId = "  "
                verifiedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.INVALID)
    }

    @Test
    fun `invalid — empty string external id stranger`() {
        val row =
            member(userId = null).apply {
                externalUserId = ""
                verifiedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.INVALID)
    }

    @Test
    fun `invalid — blank external id does not surface as synced even with syncedAt set`() {
        val row =
            member(userId = null).apply {
                externalUserId = "  "
                syncedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.INVALID)
    }

    @Test
    fun `invalid — verifiedAt without syncedAt on a desired row`() {
        val row =
            member(userId = 7L).apply {
                externalUserId = "ext"
                verifiedAt = at
            }
        assertThat(row.state).isEqualTo(TargetMemberState.INVALID)
    }

    @Test
    fun `needsPush is true only for a desired row`() {
        assertThat(member(userId = 7L).needsPush).isTrue()
        assertThat(
            member(userId = 7L)
                .apply {
                    externalUserId = "ext"
                    syncedAt = at
                }.needsPush,
        ).isFalse()
    }

    private fun member(userId: Long?): TargetMember = TargetMember(target = target, userId = userId, cohort = cohort)
}

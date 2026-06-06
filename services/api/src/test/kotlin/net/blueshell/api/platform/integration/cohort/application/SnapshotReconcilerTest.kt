package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.application.SnapshotReconciler.MemberSnapshot
import net.blueshell.api.platform.integration.cohort.persistence.CohortMemberState
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Pure unit test for the 4-way snapshot match. No DB, no Spring, no
 * transaction — it exercises [SnapshotReconciler] over plain DTOs and
 * asserts the emitted action keys, covering every branch of the original
 * `applySnapshot` ordering.
 */
class SnapshotReconcilerTest {

    private val reconciler = SnapshotReconciler()
    private val now: LocalDateTime = LocalDateTime.parse("2026-06-05T10:00:00")

    private fun desired(memberId: Long, userId: Long, state: CohortMemberState, extId: String? = null) =
        MemberSnapshot(memberId, userId, externalUserId = extId, state = state, label = null)

    private fun stranger(memberId: Long, extId: String, label: String? = null) =
        MemberSnapshot(memberId, userId = null, externalUserId = extId, state = CohortMemberState.STRANGER, label = label)

    @Test
    fun `confirm-present verifies the desired row and removes its matching stranger`() {
        val row = desired(1L, userId = 10L, state = CohortMemberState.SYNCED)
        val actions = reconciler.reconcile(
            desired = listOf(row),
            externalIdByUserId = mapOf(10L to "ext-1"),
            remote = listOf(MemberRef("ext-1", "Alice")),
            strangers = emptyList(),
            now = now,
        )

        assertThat(actions.markVerified)
            .containsExactly(SnapshotReconciler.MarkVerified(1L, "ext-1", "Alice"))
        assertThat(actions.removeStrangers)
            .containsExactly(SnapshotReconciler.RemoveStranger(externalUserId = "ext-1"))
        assertThat(actions.markDrifted).isEmpty()
        assertThat(actions.enqueueContactSync).isEmpty()
        assertThat(actions.enqueueMembershipAdd).isEmpty()
        // ext-1 is confirmed → never re-upserted as a stranger.
        assertThat(actions.upsertStrangers).isEmpty()
    }

    @Test
    fun `vanished synced row is demoted and an ADD is enqueued`() {
        val row = desired(2L, userId = 20L, state = CohortMemberState.VERIFIED, extId = "ext-2")
        val actions = reconciler.reconcile(
            desired = listOf(row),
            externalIdByUserId = mapOf(20L to "ext-2"),
            remote = emptyList(),
            strangers = emptyList(),
            now = now,
        )

        assertThat(actions.markVerified).isEmpty()
        assertThat(actions.markDrifted).containsExactly(2L)
        // has ext id but absent remotely → re-push via membership ADD.
        assertThat(actions.enqueueMembershipAdd).containsExactly(20L)
        assertThat(actions.enqueueContactSync).isEmpty()
    }

    @Test
    fun `missing row with no external id enqueues a contact sync only`() {
        val row = desired(3L, userId = 30L, state = CohortMemberState.DESIRED)
        val actions = reconciler.reconcile(
            desired = listOf(row),
            externalIdByUserId = emptyMap(),
            remote = emptyList(),
            strangers = emptyList(),
            now = now,
        )

        assertThat(actions.enqueueContactSync).containsExactly(30L)
        assertThat(actions.enqueueMembershipAdd).isEmpty()
        // DESIRED (not SYNCED/VERIFIED) → not demoted.
        assertThat(actions.markDrifted).isEmpty()
    }

    @Test
    fun `remote-only id with no desired owner is upserted as a stranger`() {
        val actions = reconciler.reconcile(
            desired = emptyList(),
            externalIdByUserId = emptyMap(),
            remote = listOf(MemberRef("ext-extra", "Extra")),
            strangers = emptyList(),
            now = now,
        )

        assertThat(actions.upsertStrangers)
            .containsExactly(SnapshotReconciler.UpsertStranger("ext-extra", "Extra"))
        assertThat(actions.removeStrangers).isEmpty()
    }

    @Test
    fun `stale stranger absent remotely is removed by its row id`() {
        val stale = stranger(7L, "stale")
        val actions = reconciler.reconcile(
            desired = emptyList(),
            externalIdByUserId = emptyMap(),
            remote = emptyList(),
            strangers = listOf(stale),
            now = now,
        )

        assertThat(actions.removeStrangers)
            .containsExactly(SnapshotReconciler.RemoveStranger(memberId = 7L))
        assertThat(actions.upsertStrangers).isEmpty()
    }

    @Test
    fun `a confirmed remote id is neither re-upserted nor left behind as a stale stranger`() {
        val row = desired(1L, userId = 10L, state = CohortMemberState.SYNCED)
        // A preloaded stranger for the same ext id as a confirmed desired row:
        // it was already collapsed in confirm-present, so it must NOT be
        // re-removed as stale, and ext-1 must NOT be re-upserted.
        val collapsedStranger = stranger(5L, "ext-1", "old")
        val actions = reconciler.reconcile(
            desired = listOf(row),
            externalIdByUserId = mapOf(10L to "ext-1"),
            remote = listOf(MemberRef("ext-1", "Alice"), MemberRef("ext-extra", "Extra")),
            strangers = listOf(collapsedStranger),
            now = now,
        )

        assertThat(actions.markVerified)
            .containsExactly(SnapshotReconciler.MarkVerified(1L, "ext-1", "Alice"))
        // Only the by-extId removal from confirm-present; the preloaded stranger
        // row (id 5) is excluded from the stale sweep because its ext id is confirmed.
        assertThat(actions.removeStrangers)
            .containsExactly(SnapshotReconciler.RemoveStranger(externalUserId = "ext-1"))
        // ext-1 confirmed → only the genuinely remote-only ext-extra is upserted.
        assertThat(actions.upsertStrangers)
            .containsExactly(SnapshotReconciler.UpsertStranger("ext-extra", "Extra"))
    }
}

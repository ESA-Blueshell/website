package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.CohortMemberState
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import java.time.LocalDateTime

/**
 * Pure, stateless reconciler for the 4-way snapshot match that powers
 * the cohort list verifier. Extracted out of `CohortRemediationService`
 * so the matching logic is unit-testable without a database,
 * transaction manager, ledger, ports, jobs, or a clock.
 *
 * It consumes plain DTOs ([MemberSnapshot], [MemberRef]) and emits
 * action KEYS ([ReconcileActions]) — never JPA entities. The caller
 * (the service, inside its write transaction) replays those keys against
 * the ledger and the job dispatcher. This keeps the matching semantics
 * free of any framework or persistence concern.
 *
 * The ordering of the four phases is load-bearing and mirrors the
 * original `applySnapshot` exactly:
 *  a. confirm-present desired rows (and collapse their matching stranger),
 *  b. demote vanished synced/verified desired rows,
 *  c. enqueue follow-ups for desired rows absent remotely,
 *  d. reconcile strangers against `remote.keys - confirmedExtIds`.
 */
class SnapshotReconciler {

    /**
     * A desired row (has `userId`) or a preloaded stranger row (no
     * `userId`) flattened to the scalars the matcher needs. No JPA
     * entity crosses this boundary.
     */
    data class MemberSnapshot(
        val memberId: Long,
        val userId: Long?,
        val externalUserId: String?,
        val state: CohortMemberState,
        val label: String?,
    )

    /** One verify action: stamp the desired row present with its external id + remote label. */
    data class MarkVerified(val memberId: Long, val externalUserId: String, val label: String?)

    /** One upsert action: a remote id with no desired owner becomes/refreshes a stranger row. */
    data class UpsertStranger(val externalUserId: String, val label: String?)

    /** Remove a stranger — by the matching external id (confirm-present) or a loaded row id (stale). */
    data class RemoveStranger(val memberId: Long? = null, val externalUserId: String? = null)

    /**
     * The decided action keys, accumulated in the original `applySnapshot`
     * order. The service replays these against the ledger and dispatcher.
     */
    data class ReconcileActions(
        val markVerified: List<MarkVerified> = emptyList(),
        val markDrifted: List<Long> = emptyList(),
        val enqueueContactSync: List<Long> = emptyList(),
        val enqueueMembershipAdd: List<Long> = emptyList(),
        val upsertStrangers: List<UpsertStranger> = emptyList(),
        val removeStrangers: List<RemoveStranger> = emptyList(),
    )

    /**
     * @param desired desired rows (a row per local user wanted in the cohort).
     * @param externalIdByUserId the external id linked to each desired user, if any.
     * @param remote the live external member list.
     * @param strangers preloaded stranger rows (no local user) for this cohort.
     * @param now timestamp the caller generated; passed through for symmetry
     *   with the ledger (the reconciler itself never reads a clock).
     */
    fun reconcile(
        desired: List<MemberSnapshot>,
        externalIdByUserId: Map<Long, String?>,
        remote: List<MemberRef>,
        strangers: List<MemberSnapshot>,
        @Suppress("UNUSED_PARAMETER") now: LocalDateTime,
    ): ReconcileActions {
        val remoteByExtId = remote.associateBy { it.externalUserId }
        val remoteExtIds = remoteByExtId.keys

        val markVerified = mutableListOf<MarkVerified>()
        val markDrifted = mutableListOf<Long>()
        val enqueueContactSync = mutableListOf<Long>()
        val enqueueMembershipAdd = mutableListOf<Long>()
        val upsertStrangers = mutableListOf<UpsertStranger>()
        val removeStrangers = mutableListOf<RemoveStranger>()

        // a. confirm-present: desired rows present in the snapshot get verified,
        //    their matching stranger collapsed, and their ext id confirmed.
        val confirmedExtIds = mutableSetOf<String>()
        desired.forEach { row ->
            val extId = externalIdByUserId[row.userId] ?: return@forEach
            val remoteMember = remoteByExtId[extId] ?: return@forEach
            markVerified += MarkVerified(row.memberId, extId, remoteMember.label)
            removeStrangers += RemoveStranger(externalUserId = extId)
            confirmedExtIds += extId
        }

        // b. demote vanished: synced/verified desired rows now absent re-bucket as missing.
        desired.forEach { row ->
            val extId = externalIdByUserId[row.userId]
            val absent = extId == null || extId !in remoteExtIds
            if (absent && (row.state == CohortMemberState.SYNCED || row.state == CohortMemberState.VERIFIED)) {
                markDrifted += row.memberId
            }
        }

        // c. enqueue follow-ups for desired rows absent remotely.
        desired.forEach { row ->
            val extId = externalIdByUserId[row.userId]
            if (extId == null) {
                enqueueContactSync += row.userId!!
            } else if (extId !in remoteExtIds) {
                enqueueMembershipAdd += row.userId!!
            }
        }

        // d. reconcile strangers. Remote ids with no confirmed desired owner are
        //    upserted; preloaded strangers absent remotely are removed. A preloaded
        //    stranger whose ext id is in confirmedExtIds was already collapsed in (a),
        //    so it is excluded before the stale-stranger sweep.
        (remoteExtIds - confirmedExtIds).forEach { extId ->
            upsertStrangers += UpsertStranger(extId, remoteByExtId[extId]?.label)
        }
        strangers
            .filter { it.externalUserId !in confirmedExtIds }
            .filter { it.externalUserId !in remoteExtIds }
            .forEach { removeStrangers += RemoveStranger(memberId = it.memberId) }

        return ReconcileActions(
            markVerified = markVerified,
            markDrifted = markDrifted,
            enqueueContactSync = enqueueContactSync,
            enqueueMembershipAdd = enqueueMembershipAdd,
            upsertStrangers = upsertStrangers,
            removeStrangers = removeStrangers,
        )
    }
}

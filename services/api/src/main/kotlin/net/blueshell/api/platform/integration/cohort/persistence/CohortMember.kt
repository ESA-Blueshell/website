package net.blueshell.api.platform.integration.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * Unified membership ledger. Two nullable timestamps name two distinct
 * facts, so a row's state is unambiguous:
 *
 * - `syncedAt`   — we successfully pushed this member to the external
 *   system (owned by the per-member sync path).
 * - `verifiedAt` — a reconcile confirmed the member present in a live
 *   remote snapshot (owned by the verifier).
 *
 * Callers classify a row through the computed [CohortMemberState]
 * ([state]) rather than reading these nullable fields by hand:
 * `DESIRED`, `SYNCED`, `VERIFIED` for the desired-row lifecycle and
 * `STRANGER` for an externally-present row with no local user.
 *
 * `userId` is a plain Long (not `@ManyToOne User`) so cohort code
 * stays decoupled from the `domain.user` entity graph.
 */
@Entity
@Table(
    name = "cohort_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_cohort_member",
            columnNames = ["cohort_id", "user_id", "deleted_at"],
        ),
        UniqueConstraint(
            name = "uk_cohort_member_external",
            columnNames = ["cohort_id", "external_user_id", "deleted_at"],
        ),
    ],
    indexes = [
        Index(name = "idx_cohort_member_cohort", columnList = "cohort_id"),
        Index(name = "idx_cohort_member_user", columnList = "user_id"),
        Index(name = "idx_cohort_member_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_cohort_member_external", columnList = "cohort_id,external_user_id"),
        Index(name = "idx_cohort_member_synced", columnList = "cohort_id,synced_at"),
        Index(name = "idx_cohort_member_verified", columnList = "cohort_id,verified_at"),
    ],
)
@SQLDelete(sql = "UPDATE cohort_member SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class CohortMember(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    val cohort: Cohort,

    @Column(name = "user_id", nullable = true)
    val userId: Long?,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    val subject: CohortSubject,

    externalUserId: String? = null,

    syncedAt: LocalDateTime? = null,

    verifiedAt: LocalDateTime? = null,

    label: String? = null,
) : AuditedAutoIdEntity() {

    // Write-protected so the entity's own transition methods are the only
    // mutators. `internal set` (matching the base `id`) rather than
    // `private set`: the kotlin-jpa all-open plugin opens these properties,
    // and Kotlin forbids a private setter on an open property. Hibernate
    // hydrates via the backing field, so this stays correct.
    @Column(name = "external_user_id", nullable = true)
    var externalUserId: String? = externalUserId
        internal set

    @Column(name = "synced_at", nullable = true)
    var syncedAt: LocalDateTime? = syncedAt
        internal set

    @Column(name = "verified_at", nullable = true)
    var verifiedAt: LocalDateTime? = verifiedAt
        internal set

    @Column(name = "label", nullable = true)
    var label: String? = label
        internal set

    /**
     * Classification of this row, computed from its nullable fields so
     * call sites stop re-deriving it by hand. `@Transient` is mandatory:
     * without a backing field Hibernate would otherwise treat this getter
     * as a persistent property and break the metamodel.
     */
    @get:jakarta.persistence.Transient
    val state: CohortMemberState
        get() = when {
            userId == null && externalUserId.isNullOrBlank() -> CohortMemberState.INVALID
            userId == null && verifiedAt == null -> CohortMemberState.INVALID
            userId == null -> CohortMemberState.STRANGER
            verifiedAt != null && syncedAt == null -> CohortMemberState.INVALID
            verifiedAt != null -> CohortMemberState.VERIFIED
            syncedAt != null -> CohortMemberState.SYNCED
            else -> CohortMemberState.DESIRED
        }

    /** A desired row still awaiting its first successful push. */
    @get:jakarta.persistence.Transient
    val needsPush: Boolean get() = state == CohortMemberState.DESIRED

    /**
     * A successful per-member push: stamp the external id and `syncedAt`
     * on this desired row.
     */
    internal fun markPushed(externalUserId: String, at: LocalDateTime) {
        this.externalUserId = externalUserId
        this.syncedAt = at
    }

    /**
     * Reconcile confirmed this desired row present in the live snapshot:
     * stamp `verifiedAt`, ensure `syncedAt` is set (present implies
     * pushed), and record the external id + label.
     */
    internal fun markVerified(externalUserId: String, label: String?, at: LocalDateTime) {
        this.externalUserId = externalUserId
        if (syncedAt == null) syncedAt = at
        verifiedAt = at
        this.label = label
    }

    /**
     * Reconcile found this previously-pushed desired row absent remotely:
     * clear both stamps so it re-buckets as not-synced.
     */
    internal fun markDrifted() {
        syncedAt = null
        verifiedAt = null
    }

    /**
     * Claim fold: move a stranger's external state onto this desired row
     * (the member is confirmed present, so it counts as synced + verified).
     */
    internal fun foldFrom(stranger: CohortMember) {
        externalUserId = stranger.externalUserId
        syncedAt = stranger.verifiedAt
        verifiedAt = stranger.verifiedAt
        label = stranger.label
    }

    /** Re-stamp an existing stranger row from a fresh remote snapshot. */
    internal fun refreshStranger(label: String?, at: LocalDateTime) {
        verifiedAt = at
        this.label = label
    }
}

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

    @Column(name = "external_user_id", nullable = true)
    var externalUserId: String? = null,

    @Column(name = "synced_at", nullable = true)
    var syncedAt: LocalDateTime? = null,

    @Column(name = "verified_at", nullable = true)
    var verifiedAt: LocalDateTime? = null,

    @Column(name = "label", nullable = true)
    var label: String? = null,
) : AuditedAutoIdEntity()

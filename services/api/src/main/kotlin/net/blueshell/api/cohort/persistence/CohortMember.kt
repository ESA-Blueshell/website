package net.blueshell.api.cohort.persistence

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
 * Unified membership ledger, where two nullable timestamps name two distinct facts: `syncedAt`
 * that the sync path pushed this member, `verifiedAt` that a reconcile found them in a live
 * remote snapshot.
 *
 * Read a row's [state] rather than these fields by hand. `userId` is a plain Long rather than a
 * `@ManyToOne User`, so cohort code stays off the user entity graph.
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
@SQLDelete(sql = "UPDATE cohort_member SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
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

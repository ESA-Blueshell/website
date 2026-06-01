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

/**
 * Join row: user U is currently in cohort C. The DB row is the source
 * of truth — sync targets converge the external state to whatever set
 * of `(user, cohort)` rows is live here. Soft-delete preserves history
 * of past memberships for debugging and audit.
 *
 * `user_id` is stored as a plain FK rather than `@ManyToOne User` so
 * cohort code stays decoupled from the `domain.user` entity graph.
 */
@Entity
@Table(
    name = "cohort_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_cohort_member",
            columnNames = ["cohort_id", "user_id", "deleted_at"],
        ),
    ],
    indexes = [
        Index(name = "idx_cohort_member_cohort", columnList = "cohort_id"),
        Index(name = "idx_cohort_member_user", columnList = "user_id"),
        Index(name = "idx_cohort_member_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE cohort_member SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class CohortMember(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    val cohort: Cohort,

    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : AuditedAutoIdEntity()

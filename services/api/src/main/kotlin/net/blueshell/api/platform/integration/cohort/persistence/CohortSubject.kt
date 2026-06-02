package net.blueshell.api.platform.integration.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * The logical thing the engine syncs — "Web Cmte", "Members 2025-2026",
 * "Newsletter Subscribers". One subject can map to zero or more
 * [Cohort]s, one per external system (Brevo list, Discord role, Google
 * group). Rules and member rows attach to the subject, not the mapping,
 * so adding a Discord mapping later does not require duplicating
 * membership state.
 */
@Entity
@Table(
    name = "cohort_subject",
    indexes = [
        Index(name = "idx_cohort_subject_type", columnList = "type, deleted_at"),
        Index(name = "idx_cohort_subject_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE cohort_subject SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class CohortSubject(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    var type: CohortSubjectType,

    @Column(name = "label", nullable = false)
    var label: String,

    @Column(name = "description")
    var description: String? = null,
) : AuditedAutoIdEntity()

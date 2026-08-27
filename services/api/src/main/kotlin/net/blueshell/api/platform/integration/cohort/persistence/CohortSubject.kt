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
 * The logical audience the engine syncs — "Web Cmte", "Members 2025-2026", "Newsletter
 * Subscribers". One subject maps to zero or more [Cohort]s, one per external system.
 *
 * What makes somebody a member is not stored here. It is a definition in code, named by
 * [definitionKey]: the subject exists so a cohort can be linked to a target and can hold a
 * membership ledger. A subject whose key names no definition any more is orphaned — its
 * committee was disbanded, say — and is reported rather than deleted, because the list it
 * points at may still be wanted.
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

    /**
     * Which definition produces this cohort: `PERIOD_MEMBERS:14`, `COMMITTEE_MEMBERS:7`,
     * `NEWSLETTER_SUBSCRIBERS`. Null only on rows soft-deleted before the key existed.
     */
    @Column(name = "definition_key", nullable = true, length = 64)
    var definitionKey: String? = null,

    @Column(name = "description")
    var description: String? = null,
) : AuditedAutoIdEntity()

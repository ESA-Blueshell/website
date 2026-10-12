package net.blueshell.api.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * A group of people defined by a rule in code, "Web Cmte" or "Members 2025-2026", mirrored by at
 * most one [Target] per external system. Stored in `cohort_subject` until that table is renamed.
 *
 * Who belongs is not stored here but decided by the definition [definitionKey] names; this row
 * exists so the cohort can have targets and a membership ledger. A cohort whose key names no
 * definition any more is orphaned and reported rather than deleted, because its targets may
 * still be wanted.
 */
@Entity
@Table(
    name = "cohort_subject",
    indexes = [
        Index(name = "idx_cohort_subject_type", columnList = "type, deleted_at"),
        Index(name = "idx_cohort_subject_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE cohort_subject SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
class Cohort(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    var type: CohortType,
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

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
 * The logical audience the engine syncs — "Web Cmte", "Members 2025-2026",
 * "Newsletter Subscribers". One subject can map to zero or more
 * [Cohort]s, one per external system (Brevo list, Discord role, Google
 * group).
 *
 * The subject owns the rule that selects its members: users whose facts
 * include `(factKind, factKey)` belong to it, when [enabled]. There is one
 * subject per `(factKind, factKey)` (unique key `uk_cohort_subject_fact`).
 * Both columns are nullable so an operator-created [CohortSubjectType.CUSTOM]
 * subject can exist before its fact pair is populated.
 *
 * The subject-level *engine* sketched by V72 — where membership attaches to
 * the subject so a second system reuses one desired set — is still **not**
 * finished: [CohortMember] carries `cohort_id` and the evaluator diffs cohort
 * rows. This change moves only the rule columns onto the subject.
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

    /** Fact kind this subject's rule pivots on; null for an unconfigured CUSTOM subject. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fact_kind", nullable = true, length = 32)
    var factKind: CohortFactKind? = null,

    /** Fact key (committee id, period id, "true", a Role name); null until set. */
    @Column(name = "fact_key", nullable = true, length = 64)
    var factKey: String? = null,

    /** When false the rule is dormant — the evaluator skips this subject. */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
) : AuditedAutoIdEntity()

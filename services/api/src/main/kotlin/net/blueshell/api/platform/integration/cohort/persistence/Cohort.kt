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
 * A named group on one external system: a defined population of users
 * sharing one or more facts. Brevo lists, Discord roles and Google
 * groups all map to one row here. The native-side id lives in the
 * existing `external_id_mapping` table with `aggregate_type='COHORT'`,
 * so a brand-new cohort can exist locally before it has been
 * materialised externally (and adapters create it lazily on first use).
 *
 * `system` is stored as a plain string holding a `TargetSystem.name()`;
 * the persistence layer cannot depend on the `sync.port` package per the
 * layered architecture rule, matching how `ExternalIdMapping.system` is
 * modelled.
 */
@Entity
@Table(
    name = "cohort",
    indexes = [
        Index(name = "idx_cohort_system_kind", columnList = "system, kind, deleted_at"),
        Index(name = "idx_cohort_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE cohort SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Cohort(
    @Column(name = "system", nullable = false, length = 32)
    var system: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    var kind: CohortKind,

    @Column(name = "label", nullable = false)
    var label: String,
) : AuditedAutoIdEntity()

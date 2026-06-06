package net.blueshell.api.platform.integration.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * A named group on one external system: a defined population of users
 * sharing one or more facts. Brevo lists, Discord roles and Google
 * groups all map to one row here. The native-side id lives in
 * [externalId] (owned by `CohortTargetIds`), which is `null` until the
 * cohort has been materialised externally by the `cohort.materialize-target`
 * job. During the compatibility window `CohortTargetIds` also falls back to
 * the legacy `external_id_mapping` row with `aggregate_type='COHORT'`.
 *
 * `system` is the [TargetSystem] enum, persisted via `@Enumerated(STRING)`
 * as the enum name ("BREVO"/"GOOGLE_CALENDAR") in the same `VARCHAR(32)`
 * column — legal because the enum lives in the Shared layer, which the
 * persistence layer may depend on. (`ExternalIdMapping.system` is a
 * separate, still-String concern and is deliberately left untyped.)
 */
@Entity
@Table(
    name = "cohort",
    indexes = [
        Index(name = "idx_cohort_system_kind", columnList = "system, kind, deleted_at"),
        Index(name = "idx_cohort_folder", columnList = "folder"),
        Index(name = "idx_cohort_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE cohort SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Cohort(
    @Enumerated(EnumType.STRING)
    @Column(name = "system", nullable = false, length = 32)
    var system: TargetSystem,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    var kind: CohortKind,

    @Column(name = "label", nullable = false)
    var label: String,

    /**
     * Optional folder name used to group cohorts in the admin UI. Mirrors
     * the folder concept on Brevo (and later Discord category / Google
     * group org-unit) — the column carries the canonical display name and
     * the per-target adapter is responsible for translating that into the
     * vendor's folder id when materialising the external counterpart.
     * `null` means the cohort sits at the top level / "Other" group.
     */
    @Column(name = "folder", nullable = true, length = 64)
    var folder: String? = null,

    /**
     * Parent subject. After V72 every active cohort row is a per-system
     * mapping under one subject, but the column is still nullable in the
     * persistence layer so soft-deleted rows from before the migration
     * remain readable. New rows MUST populate this — the resolvers do.
     */
    @Column(name = "subject_id", nullable = true)
    var subjectId: Long? = null,

    /**
     * Native id of this cohort's target on [system] (e.g. a Brevo list id).
     * `null` until materialised. Written only through `CohortTargetIds`;
     * `1024` matches the widened `external_id_mapping.external_id` (V61).
     */
    @Column(name = "external_id", nullable = true, length = 1024)
    var externalId: String? = null,
) : AuditedAutoIdEntity()

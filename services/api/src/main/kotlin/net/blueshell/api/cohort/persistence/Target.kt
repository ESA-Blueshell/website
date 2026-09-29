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
import java.time.Instant

/**
 * The list, role or group on one external system that mirrors one cohort; stored in `cohort`
 * until that table is renamed. [externalId] is null until the create-target job, or the board,
 * creates or links it on the system, and `CohortTargetIds` owns it.
 *
 * `system` is a plain string holding a `TargetSystem.name()`: persistence cannot depend on the
 * `sync.port` package under the layered architecture rule.
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
@SQLDelete(sql = "UPDATE cohort SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
class Target(
    @Column(name = "system", nullable = false, length = 32)
    var system: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    var kind: TargetKind,
    @Column(name = "label", nullable = false)
    var label: String,
    /**
     * Optional folder name used to group cohorts in the admin UI. Mirrors
     * the folder concept on Brevo (and later Discord category / Google
     * group org-unit) — the column carries the canonical display name and
     * explicit target creation is responsible for translating that into the
     * vendor's folder id.
     * `null` means the cohort sits at the top level / "Other" group.
     */
    @Column(name = "folder", nullable = true, length = 64)
    var folder: String? = null,
    /** The cohort this target mirrors; nullable only so soft-deleted rows from before V72 stay readable. */
    @Column(name = "subject_id", nullable = true)
    var cohortId: Long? = null,
    /**
     * Native id of this cohort's target on [system] (e.g. a Brevo list id).
     * `null` until explicitly created or linked. Written only through `CohortTargetIds`;
     * `1024` matches the widened `external_id_mapping.external_id` (V61).
     */
    @Column(name = "external_id", nullable = true, length = 1024)
    var externalId: String? = null,
    /** When a create-target job first set out to make this target; a retry that finds it looks the target up first. */
    @Column(name = "target_claimed_at", nullable = true)
    var targetClaimedAt: Instant? = null,
    /** Whether each reconcile removes the target's theirs-only people. Only an admin sets it. */
    @Column(name = "enforced", nullable = false)
    var enforced: Boolean = false,
) : AuditedAutoIdEntity()

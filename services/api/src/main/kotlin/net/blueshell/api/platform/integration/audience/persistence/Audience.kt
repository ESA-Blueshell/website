package net.blueshell.api.platform.integration.audience.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * A named group on one external system. Brevo lists, Discord roles and
 * Google groups all map to one row here. The native-side id lives in the
 * existing `external_id_mapping` table with `aggregate_type='AUDIENCE'`,
 * so a brand-new audience can exist locally before it has been
 * materialised externally (and adapters create it lazily on first use).
 */
@Entity
@Table(
    name = "audience",
    indexes = [
        Index(name = "idx_audience_system_kind", columnList = "system, kind, deleted_at"),
        Index(name = "idx_audience_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE audience SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Audience(
    @Enumerated(EnumType.STRING)
    @Column(name = "system", nullable = false, length = 32)
    var system: TargetSystem,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    var kind: AudienceGroupKind,

    @Column(name = "label", nullable = false)
    var label: String,
) : AuditedAutoIdEntity()

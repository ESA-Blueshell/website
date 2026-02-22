package net.blueshell.api.domain.telemetry.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "redirects",
    indexes = [
        Index(name = "idx_redirects_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_redirects_telemetry_id", columnList = "telemetry_id"),
        Index(name = "idx_redirects_created_at", columnList = "created_at")
    ]
)
@SQLDelete(sql = "UPDATE redirects SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Redirect(
    @JoinColumn(name = "telemetry_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    var telemetry: Telemetry,
) : AuditedAutoIdEntity() {
    val telemetryId: Long
        get() = telemetry.id ?: 0
}

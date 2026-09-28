package net.blueshell.api.telemetry.persistence

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "redirects",
    indexes = [
        Index(name = "idx_redirects_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_redirects_telemetry_id", columnList = "telemetry_id"),
        Index(name = "idx_redirects_created_at", columnList = "created_at"),
    ],
)
@SQLDelete(sql = "UPDATE redirects SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
class Redirect(
    @JoinColumn(name = "telemetry_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    var telemetry: Telemetry,
) : AuditedAutoIdEntity() {
    val telemetryId: Long
        get() = telemetry.id ?: 0
}

package net.blueshell.api.platform.integration.audience.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AutoIdEntity
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/**
 * Admin-editable rule: users whose facts include `(factKind, factKey)`
 * should be members of `audience`. The rule table is small and
 * configuration-only — no soft-delete and no audit-trail columns; admins
 * just create and remove rows.
 */
@Entity
@Table(
    name = "audience_rule",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_audience_rule",
            columnNames = ["fact_kind", "fact_key", "audience_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_audience_rule_fact_enabled", columnList = "fact_kind, fact_key, enabled"),
        Index(name = "idx_audience_rule_audience", columnList = "audience_id"),
    ],
)
class AudienceRule(
    @Enumerated(EnumType.STRING)
    @Column(name = "fact_kind", nullable = false, length = 32)
    var factKind: AudienceFactKind,

    @Column(name = "fact_key", nullable = false, length = 64)
    var factKey: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audience_id", nullable = false)
    var audience: Audience,

    @Column(name = "enabled", nullable = false)
    @ColumnDefault("true")
    var enabled: Boolean = true,
) : AutoIdEntity() {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    lateinit var createdAt: Instant
        internal set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    lateinit var updatedAt: Instant
        internal set
}

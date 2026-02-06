package net.blueshell.api.model

import jakarta.persistence.*
import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.model.base.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "telemetries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_telemetries_platform_url_deleted_at",
            columnNames = ["platform", "url", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_telemetries_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_telemetries_platform", columnList = "platform"),
        Index(name = "idx_telemetries_url", columnList = "url"),
        Index(name = "idx_telemetries_created_at", columnList = "created_at")
    ]
)
@SQLDelete(sql = "UPDATE telemetries SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Telemetry() : AuditedAutoIdEntity() {
    @field:Column(nullable = false)
    @field:Enumerated(EnumType.ORDINAL)
    lateinit var platform: PlatformType

    @field:Column(nullable = false)
    lateinit var url: String

    @OneToMany(mappedBy = "_telemetry", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _redirects: MutableSet<Redirect> = linkedSetOf()
    val redirects: Set<Redirect>
        get() = _redirects

    constructor(platform: PlatformType, url: String) : this() {
        this.platform = platform
        this.url = url
    }
}

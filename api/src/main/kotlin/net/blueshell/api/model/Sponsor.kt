package net.blueshell.api.model

import jakarta.persistence.*
import net.blueshell.api.model.base.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "sponsors",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sponsors_name_deleted_at", columnNames = ["name", "deleted_at"]),
        UniqueConstraint(name = "uk_sponsors_logo_deleted_at", columnNames = ["logo_id", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_sponsors_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_sponsors_logo_id", columnList = "logo_id")
    ]
)
@SQLDelete(sql = "UPDATE sponsors SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
open class Sponsor : AuditedAutoIdEntity() {
    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false, length = 4095)
    lateinit var description: String

    @field:OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @field:JoinColumn(name = "logo_id", nullable = false, insertable = false, updatable = false)
    private var _picture: File? = null
    var picture: File
        get() = requireNotNull(_picture) { "Picture is required" }
        set(value) {
            _picture = value
            pictureId = value.id ?: pictureId
        }

    @Column(name = "logo_id", nullable = false)
    var pictureId: Long = 0
}

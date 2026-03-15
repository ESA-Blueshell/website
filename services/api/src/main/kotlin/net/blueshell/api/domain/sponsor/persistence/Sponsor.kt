package net.blueshell.api.domain.sponsor.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.model.AuditedAutoIdEntity

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
class Sponsor(

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, length = 4095)
    var description: String

) : AuditedAutoIdEntity() {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_id", nullable = false)
    lateinit var picture: File
        internal set

    val pictureId: Long
        get() = picture.id ?: 0
}

package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "guests",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_guests_access_token_deleted_at", columnNames = ["access_token", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_guests_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_guests_name", columnList = "name"),
        Index(name = "idx_guests_discord", columnList = "discord"),
        Index(name = "idx_guests_created_at", columnList = "created_at")
    ]
)
@SQLDelete(sql = "UPDATE guests SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Guest : AuditedAutoIdEntity() {
    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false)
    lateinit var discord: String

    @Column(nullable = false)
    lateinit var email: String

    @Column
    var phoneNumber: String? = null

    @Column(name = "access_token", nullable = false)
    var accessToken: String? = null
}

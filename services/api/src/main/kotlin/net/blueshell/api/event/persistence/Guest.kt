package net.blueshell.api.event.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "guests",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_guests_access_token_hash_deleted_at", columnNames = ["access_token_hash", "deleted_at"])
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
class Guest(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var discord: String,

    @Column(nullable = false)
    var email: String,

    @Column
    var phoneNumber: String? = null,

    @Column(name = "access_token_hash", nullable = false, length = 64)
    var accessTokenHash: String,
) : AuditedAutoIdEntity() {
    @Transient
    var accessTokenRaw: String? = null

    fun matchesAccessToken(rawToken: String): Boolean {
        return accessTokenHash == GuestAccessTokenCodec.hash(rawToken)
    }

    companion object {
        fun withRawToken(
            name: String,
            discord: String,
            email: String,
            phoneNumber: String? = null,
            accessToken: String,
        ): Guest {
            return Guest(
                name = name,
                discord = discord,
                email = email,
                phoneNumber = phoneNumber,
                accessTokenHash = GuestAccessTokenCodec.hash(accessToken),
            ).also { it.accessTokenRaw = accessToken }
        }
    }
}

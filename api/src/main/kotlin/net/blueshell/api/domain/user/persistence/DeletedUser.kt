package net.blueshell.api.domain.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "deleted_users")
class DeletedUser(
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "username", nullable = false)
    var username: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "initials", nullable = false)
    var initials: String,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "prefix")
    var prefix: String? = null,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "discord")
    var discord: String? = null,

    @Column(name = "newsletter", nullable = false)
    var newsletter: Boolean,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,

    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant,

    @Column(name = "restore_until_at", nullable = false)
    var restoreUntilAt: Instant,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        internal set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        internal set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L

    val fullName: String
        get() = listOfNotNull(firstName, prefix?.takeIf { it.isNotBlank() }, lastName).joinToString(" ")

    companion object {
        fun fromUser(
            user: User,
            deletedAt: Instant,
            restoreUntilAt: Instant
        ): DeletedUser {
            return DeletedUser(
                userId = user.id!!,
                username = user.username,
                email = user.email,
                initials = user.initials,
                firstName = user.firstName,
                prefix = user.prefix,
                lastName = user.lastName,
                phoneNumber = user.phoneNumber,
                discord = user.discord,
                newsletter = user.newsletter,
                enabled = user.enabled,
                deletedAt = deletedAt,
                restoreUntilAt = restoreUntilAt
            )
        }
    }
}

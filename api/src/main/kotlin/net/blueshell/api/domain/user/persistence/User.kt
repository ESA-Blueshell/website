package net.blueshell.api.domain.user.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.AuditedAutoIdEntity

import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.sql.Date

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_username_deleted_at", columnNames = ["username", "deleted_at"]),
        UniqueConstraint(name = "uk_users_email_deleted_at", columnNames = ["email", "deleted_at"]),
        UniqueConstraint(name = "uk_users_discord_deleted_at", columnNames = ["discord", "deleted_at"]),
        UniqueConstraint(name = "uk_users_phone_number_deleted_at", columnNames = ["phone_number", "deleted_at"]),
        UniqueConstraint(name = "uk_users_address_id_deleted_at", columnNames = ["address_id", "deleted_at"]),
        UniqueConstraint(
            name = "uk_users_profile_picture_id_deleted_at",
            columnNames = ["profile_picture_id", "deleted_at"]
        ),
    ],
    indexes = [
        Index(name = "idx_users_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_users_created_at", columnList = "created_at"),
        Index(name = "idx_users_enabled", columnList = "enabled"),
        Index(name = "idx_users_newsletter", columnList = "newsletter"),
        Index(name = "idx_users_last_name", columnList = "last_name"),
        Index(name = "idx_users_first_name", columnList = "first_name"),
    ]
)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class User(

    @Column(nullable = false, unique = false)
    var username: String,

    @Column(nullable = false)
    var password: String,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Column
    var prefix: String? = null,

    @Column(nullable = false)
    var initials: String,

    @Column(name = "phone_number")
    var phoneNumber: String,

    @Column(nullable = false)
    var discord: String,

    @Column
    var steamid: String? = null,

    @Column(nullable = false)
    var newsletter: Boolean = false,

    // Accounts are activated exclusively through recovery controller's activate endpoint
    // All newly created users are disabled by default and must activate via email link
    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(name = "consent_privacy")
    var consentPrivacy: Boolean = false,

    @Column(name = "consent_gdpr")
    var consentGdpr: Boolean = false,

    // Roles are managed through multiple mechanisms:
    // - GUEST: Default role assigned on user creation
    // - MEMBER: Granted through membership creation/management
    // - COMMITTEE: Granted through committee membership
    // - BOARD/ADMIN: Granted through ToggleUserRole endpoint (requires ADMIN)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    var roles: MutableSet<Role> = mutableSetOf(Role.GUEST),

    @Column(name = "contact_id")
    var contactId: Long? = null,

    ) : AuditedAutoIdEntity() {
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    final var address: Address? = null
        private set

    val addressId: Long?
        get() = address?.id

    @Column(nullable = false, unique = true)
    var email: String = ""
        set(value) {
            field = value.trim().lowercase()
        }
        get() = field.trim().lowercase()

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_id")
    final var profilePicture: File? = null
        private set

    val profilePictureId: Long?
        get() = profilePicture?.id

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    final var personDetails: PersonDetails? = null
        private set

    val personDetailsId: Long?
        get() = personDetails?.id

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _recoveryTokens: MutableSet<RecoveryToken> = linkedSetOf()
    val recoveryTokens: Set<RecoveryToken>
        get() = _recoveryTokens

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    private val _committeeMembers: MutableSet<CommitteeMember> = linkedSetOf()
    val committeeMembers: Set<CommitteeMember>
        get() = _committeeMembers

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _contributions: MutableSet<Contribution> = linkedSetOf()
    val contributions: Set<Contribution>
        get() = _contributions

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _memberships: MutableSet<Membership> = linkedSetOf()
    val memberships: Set<Membership>
        get() = _memberships

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _eventSignUps: MutableSet<EventSignUp> = linkedSetOf()
    val eventSignUps: Set<EventSignUp>
        get() = _eventSignUps

    val committeeIds: Set<Long>
        get() = committeeMembers.mapNotNull { it.committee.id }.toSet()

    val inheritedRoles: Set<Role>
        get() = roles.flatMap { it.allInheritedRoles }.toSet()

    fun hasRole(role: Role): Boolean = roles.any { it == role }

    fun hasAuthority(role: Role): Boolean = inheritedRoles.any { it.matchesRole(role) }

    val authorities: Collection<GrantedAuthority>
        get() = inheritedRoles
            .map { SimpleGrantedAuthority(it.reprString) }
            .toMutableSet()

    fun addRole(role: Role) {
        roles.add(role)
    }

    fun removeRole(role: Role) {
        roles.remove(role)
    }

    val fullName: String
        get() = listOfNotNull(firstName, prefix?.takeIf { it.isNotBlank() }, lastName).joinToString(" ")
}

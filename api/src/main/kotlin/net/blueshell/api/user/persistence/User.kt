package net.blueshell.api.user.persistence

import jakarta.persistence.*
import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.file.persistence.File
import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.asRef
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
    var username: String = "",

    @Column(nullable = false)
    var password: String = "",

    @Column(name = "first_name", nullable = false)
    var firstName: String = "",

    @Column(name = "last_name", nullable = false)
    var lastName: String = "",

    @Column
    var prefix: String? = null,

    @Column
    var initials: String? = null,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "student_number")
    var studentNumber: String? = null,

    @Column(name = "date_of_birth")
    var dateOfBirth: Date? = null,

    @Column
    var discord: String? = null,

    @Column
    var steamid: String? = null,

    @Column(nullable = false)
    var newsletter: Boolean = false,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(name = "consent_privacy")
    var consentPrivacy: Boolean = false,

    @Column(name = "consent_gdpr")
    var consentGdpr: Boolean = false,

    @Column
    var gender: String? = null,

    @Column(name = "photo_consent")
    var photoConsent: Boolean = false,

    @Column
    var nationality: String? = null,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    var roles: MutableSet<Role> = mutableSetOf(Role.GUEST),

    @Column(name = "ehbo")
    var ehbo: Boolean = false,

    @Column(name = "contact_id")
    var contactId: Long? = null,

    @Column(name = "bhv")
    var bhv: Boolean = false,

    @Column(name = "study")
    var study: String? = null,

    @Column(name = "start_study_year")
    var startStudyYear: Long? = null,

    ) : AuditedAutoIdEntity() {
    @field:OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @field:JoinColumn(name = "address_id")
    private var _address: Address? = null
    var address: Address?
        get() = _address
        set(value) {
            _address = value
            addressId = value?.id
        }

    @field:Column(name = "address_id", updatable = false, insertable = false)
    var addressId: Long? = null
        get() = _address?.id
        set(value) {
            field = value
            if (value == null) {
                _address = null
            } else if (value != _address?.id) {
                _address = Address::class.asRef(value)
            }
        }

    @Column(nullable = false)
    var email: String = ""
        set(value) {
            field = value.trim().lowercase()
        }
        get() = field.trim().lowercase()


    @field:OneToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "profile_picture_id")
    private var _profilePicture: File? = null
    var profilePicture: File?
        get() = _profilePicture
        set(value) {
            _profilePicture = value
            profilePictureId = value?.id
        }

    @field:Column(name = "profile_picture_id", updatable = false, insertable = false)
    var profilePictureId: Long? = null
        get() = _profilePicture?.id
        set(value) {
            field = value
            if (value == null) {
                _profilePicture = null
            } else if (value != _profilePicture?.id) {
                _profilePicture = File::class.asRef(value)
            }
        }


    @OneToMany(mappedBy = "_user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _recoveryTokens: MutableSet<RecoveryToken> = linkedSetOf()
    val recoveryTokens: Set<RecoveryToken> get() = _recoveryTokens

    @OneToMany(mappedBy = "_user", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    private val _committeeMembers: MutableSet<CommitteeMember> = linkedSetOf()
    val committeeMembers: Set<CommitteeMember> get() = _committeeMembers

    @OneToMany(mappedBy = "_user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _contributions: MutableSet<Contribution> = linkedSetOf()
    val contributions: Set<Contribution> get() = _contributions

    @OneToMany(mappedBy = "_user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _memberships: MutableSet<Membership> = linkedSetOf()
    val memberships: Set<Membership> get() = _memberships

    @OneToMany(mappedBy = "_user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _eventSignUps: MutableSet<EventSignUp> = linkedSetOf()
    val eventSignUps: Set<EventSignUp> get() = _eventSignUps

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

//    override fun getUsername(): String = username
//    fun setUsername(value: String) {
//        username = value
//    }

//    override fun getPassword(): String = password
//    fun setPassword(value: String) {
//        password = value
//    }

    fun isAccountNonExpired(): Boolean = true
    fun isAccountNonLocked(): Boolean = true
    fun isCredentialsNonExpired(): Boolean = true
    fun isEnabled(): Boolean = enabled
}

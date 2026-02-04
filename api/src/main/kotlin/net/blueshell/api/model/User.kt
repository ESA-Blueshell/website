package net.blueshell.api.model

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.common.enums.Role
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.model.event.EventSignUp
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
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
@EntityListeners(JpaListener::class)
class User(

    @Column(nullable = false, unique = false)
    private var username: String = "",

    @Column(nullable = false)
    private var password: String = "",

    @Column(name = "first_name", nullable = false)
    var firstName: String = "",

    @Column(name = "last_name", nullable = false)
    var lastName: String = "",

    @Column
    var prefix: String? = null,

    @Column
    var initials: String? = null,

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    var address: Address? = null,

    @Column(name = "address_id", updatable = false, insertable = false)
    var addressId: Long? = null,

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

    ) : BaseModel(), UserDetails {

    @Column(nullable = false)
    var email: String = ""
        set(value) {
            field = value.trim().lowercase()
        }
        get() = field.trim().lowercase()


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_id", insertable = false, updatable = false)
    val profilePicture: File? = null

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _recoveryTokens: MutableSet<RecoveryToken> = linkedSetOf()
    val recoveryTokens: Set<RecoveryToken> get() = _recoveryTokens

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    private val _committeeMembers: MutableSet<CommitteeMember> = linkedSetOf()
    val committeeMembers: Set<CommitteeMember> get() = _committeeMembers

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _contributions: MutableSet<Contribution> = linkedSetOf()
    val contributions: Set<Contribution> get() = _contributions

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _memberships: MutableSet<Membership> = linkedSetOf()
    val memberships: Set<Membership> get() = _memberships

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _eventSignUps: MutableSet<EventSignUp> = linkedSetOf()
    val eventSignUps: Set<EventSignUp> get() = _eventSignUps

    val committeeIds: Set<Long>
        get() = committeeMembers.mapNotNull { it.committee.id }.toSet()

    val inheritedRoles: Set<Role>
        get() = roles.flatMap { it.allInheritedRoles }.toSet()

    fun hasRole(role: Role): Boolean = roles.any { it == role }

    fun hasAuthority(role: Role): Boolean = inheritedRoles.any { it.matchesRole(role) }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        inheritedRoles
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

    override fun getUsername(): String = username
    fun setUsername(value: String) {
        username = value
    }

    override fun getPassword(): String = password
    fun setPassword(value: String) {
        password = value
    }

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = enabled
}

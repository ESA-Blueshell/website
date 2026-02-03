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
import java.util.*

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_users_username_deleted_at",
        columnNames = ["username", "deleted_at"]
    ), UniqueConstraint(name = "uk_users_email_deleted_at", columnNames = ["email", "deleted_at"]), UniqueConstraint(
        name = "uk_users_discord_deleted_at",
        columnNames = ["discord", "deleted_at"]
    ), UniqueConstraint(
        name = "uk_users_phone_number_deleted_at",
        columnNames = ["phone_number", "deleted_at"]
    ), UniqueConstraint(
        name = "uk_users_address_id_deleted_at",
        columnNames = ["address_id", "deleted_at"]
    ), UniqueConstraint(
        name = "uk_users_profile_picture_id_deleted_at",
        columnNames = ["profile_picture_id", "deleted_at"]
    )],
    indexes = [Index(name = "idx_users_deleted_at", columnList = "deleted_at"), Index(
        name = "idx_users_created_at",
        columnList = "created_at"
    ), Index(name = "idx_users_enabled", columnList = "enabled"), Index(
        name = "idx_users_newsletter",
        columnList = "newsletter"
    ), Index(name = "idx_users_last_name", columnList = "last_name"), Index(
        name = "idx_users_first_name",
        columnList = "first_name"
    )]
)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class User : BaseModel(), UserDetails {
    @Column(nullable = false)
    lateinit var username: String

    @Column(nullable = false)
    lateinit var password: String

    @Column(name = "first_name", nullable = false)
    lateinit var firstName: String

    @Column(name = "last_name", nullable = false)
    lateinit var lastName: String

    @Column
    var prefix: String? = null

    @Column
    var initials: String? = null

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    var address: Address? = null

    @Column(name = "address_id", updatable = false, insertable = false)
    var addressId: Long? = null

    @Column(name = "phone_number")
    var phoneNumber: String? = null

    @Column(nullable = false)
    var email: String = ""
        set(value) {
            field = value.lowercase(Locale.ROOT)
        }

    @Column(name = "student_number")
    var studentNumber: String? = null

    @Column(name = "date_of_birth")
    var dateOfBirth: Date? = null

    @Column
    var discord: String? = null

    @Column
    var steamid: String? = null

    @Column(nullable = false)
    var newsletter = false

    @Column(nullable = false)
    var enabled = false

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _recoveryTokens: MutableSet<RecoveryToken> = linkedSetOf()
    val recoveryTokens: Set<RecoveryToken>
        get() = _recoveryTokens

    @Column(name = "consent_privacy")
    var consentPrivacy = false

    @Column(name = "consent_gdpr")
    var consentGdpr = false

    @Column
    var gender: String? = null

    @Column(name = "photo_consent")
    var photoConsent = false

    @Column
    var nationality: String? = null

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_id", insertable = false, updatable = false)
    val profilePicture: File? = null

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    private val _committeeMembers: MutableSet<CommitteeMember> = linkedSetOf()
    val committeeMembers: Set<CommitteeMember>
        get() = _committeeMembers

    @ElementCollection(targetClass = Role::class, fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(
        EnumType.STRING
    )
    @Column(name = "authority")
    var roles: MutableSet<Role> = EnumSet.of(Role.GUEST)

    @Column(name = "ehbo")
    var ehbo = false

    @Column(name = "contact_id")
    var contactId: Long? = null

    @Column(name = "bhv")
    var bhv = false

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

    @Column(name = "study")
    var study: String? = null

    @Column(name = "start_study_year")
    var startStudyYear: Long? = null

    val committeeIds: MutableSet<Long>
        get() {
            val set: MutableSet<Long> = HashSet()
            committeeMembers.forEach { cm -> cm.committee.id?.let { set.add(it) } }
            return set
        }

    val inheritedRoles: MutableSet<Role>
        get() = HashSet(
            roles
                .stream()
                .flatMap { role: Role ->
                    role.allInheritedRoles.stream()
                }.toList()
        )

    fun hasRole(role: Role): Boolean {
        return roles.stream().anyMatch { r: Role -> r == role }
    }

    fun hasAuthority(role: Role): Boolean {
        return this.inheritedRoles.stream().anyMatch { r: Role -> r.matchesRole(role) }
    }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        val auths = HashSet<GrantedAuthority>()
        roles.stream()
            .flatMap { role: Role -> role.allInheritedRoles.stream() }
            .map { authority: Role -> SimpleGrantedAuthority(authority.reprString) }
            .forEach { e: SimpleGrantedAuthority -> auths.add(e) }

        return auths
    }

    override fun getPassword(): String = password
    override fun getUsername(): String = username
    override fun isEnabled(): Boolean = enabled

    fun addRole(role: Role) {
        roles.add(role)
    }

    fun removeRole(role: Role) {
        roles.remove(role)
    }

    val fullName: String
        get() {
            if (prefix == null || prefix!!.isEmpty()) {
                return "$firstName $lastName"
            }
            return "$firstName $prefix $lastName"
        }
}

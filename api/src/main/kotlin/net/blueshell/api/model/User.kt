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
    val address: Address? = null

    @Column(name = "address_id", updatable = false, insertable = false)
    var addressId: Long? = null

    @Column(name = "phone_number")
    var phoneNumber: String? = null

    @Column(nullable = false)
    lateinit var email: String

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

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val recoveryTokens: MutableSet<RecoveryToken?>? = null

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
    val committeeMembers: MutableSet<CommitteeMember> = HashSet<CommitteeMember>()

    @ElementCollection(targetClass = Role::class, fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(
        EnumType.STRING
    )
    @Column(name = "authority")
    var roles: MutableSet<Role?> = EnumSet.of<Role?>(Role.GUEST)

    @Column(name = "ehbo")
    var ehbo = false

    @Column(name = "contact_id")
    var contactId: Long? = null

    @Column(name = "bhv")
    var bhv = false

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val contributions: MutableSet<Contribution?>? = null

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL])
    val memberships: MutableSet<Membership?>? = null

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val eventSignUps: MutableSet<EventSignUp?>? = null

    @Column(name = "study")
    var study: String? = null

    @Column(name = "start_study_year")
    var startStudyYear: Long? = null

    val committeeIds: MutableSet<Long?>
        get() {
            val set: MutableSet<Long?> = HashSet<Long?>()
            if (getCommitteeMembers() == null) {
                return set
            }
            for (cm in getCommitteeMembers()) {
                set.add(cm.getCommittee().getId())
            }
            return set
        }

    val inheritedRoles: MutableSet<Role?>
        get() = HashSet<Role?>(
            getRoles()
                .stream()
                .flatMap<Role?> { role: Role? ->
                    role!!.getAllInheritedRoles().stream()
                }.toList()
        )

    fun hasRole(role: Role?): Boolean {
        return getRoles().stream().anyMatch { r: Role? -> r == role }
    }

    fun hasAuthority(role: Role?): Boolean {
        return this.inheritedRoles.stream().anyMatch { r: Role? -> r!!.matchesRole(role) }
    }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority?> {
        val auths = HashSet<GrantedAuthority?>()
        if (getRoles() == null) {
            return auths
        }

        getRoles().stream()
            .flatMap<Role?> { role: Role? -> role!!.getAllInheritedRoles().stream() }
            .map<SimpleGrantedAuthority?> { authority: Role? -> SimpleGrantedAuthority(authority!!.getReprString()) }
            .forEach { e: SimpleGrantedAuthority? -> auths.add(e) }

        return auths
    }

    override fun isEnabled(): Boolean = enabled

    fun addRole(role: Role?) {
        getRoles().add(role)
    }

    fun removeRole(role: Role?) {
        getRoles().remove(role)
    }

    fun setEmail(email: String) {
        this.email = email.lowercase(Locale.getDefault())
    }

    val fullName: String
        get() {
            if (prefix == null || prefix!!.isEmpty()) {
                return firstName + " " + lastName
            }
            return firstName + " " + prefix + " " + lastName
        }
}

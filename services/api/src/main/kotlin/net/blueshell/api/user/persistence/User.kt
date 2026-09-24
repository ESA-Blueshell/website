package net.blueshell.api.user.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.user.domain.GrantedRoles
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.Instant

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_username_deleted_at", columnNames = ["username", "deleted_at"]),
        UniqueConstraint(name = "uk_users_email_deleted_at", columnNames = ["email", "deleted_at"]),
        UniqueConstraint(name = "uk_users_discord_id_deleted_at", columnNames = ["discord_id", "deleted_at"]),
        UniqueConstraint(name = "uk_users_phone_number_deleted_at", columnNames = ["phone_number", "deleted_at"]),
        UniqueConstraint(name = "uk_users_address_id_deleted_at", columnNames = ["address_id", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_users_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_users_created_at", columnList = "created_at"),
        Index(name = "idx_users_enabled", columnList = "enabled"),
        Index(name = "idx_users_newsletter", columnList = "newsletter"),
        Index(name = "idx_users_last_name", columnList = "last_name"),
        Index(name = "idx_users_first_name", columnList = "first_name"),
    ],
)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class User(
    @Column(nullable = false, unique = false)
    var username: String,
    @Column(nullable = false, unique = true)
    @ColumnTransformer(read = "lower(email)", write = "lower(trim(?))")
    var email: String,
    @Column(nullable = false)
    var password: String,
    @Column(nullable = false)
    var initials: String,
    @Column(nullable = false)
    var firstName: String,
    @Column
    var prefix: String? = null,
    @Column(nullable = false)
    var lastName: String,
    @Column
    var phoneNumber: String? = null,
    /** The member's name in the association's Discord server, or what they typed before linking. */
    @Column
    var discord: String? = null,
    /** The linked Discord member, by the user ID Discord never changes. */
    @Column(name = "discord_id")
    var discordId: String? = null,
    @Column
    var steamid: String? = null,
    @Column(nullable = false)
    var newsletter: Boolean = false,
    // Accounts are activated exclusively through recovery controller's activate endpoint
    // All newly created users are disabled by default and must activate via email link
    @Column(nullable = false)
    var enabled: Boolean = false,
    @Column
    var consentPrivacy: Boolean = false,
    @Column(nullable = false)
    var photoConsent: Boolean = false,
    // Four sources, and only the last is a decision somebody made:
    // - GUEST: the default every account is created with
    // - MEMBER: follows an active membership, kept in step by MembershipEventListener
    // - COMMITTEE: follows a committee seat, kept in step by CommitteeMembershipChangedListener
    // - BOARD/TREASURER/ADMIN: granted by an admin through PUT /users/{userId}/roles, which
    //   records every change. See ADR-028.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    var roles: MutableSet<Role> = mutableSetOf(Role.GUEST),
) : AuditedAutoIdEntity() {
    /** Since when a second factor has been set up, or null while there is none (api ADR-031). */
    @Column(name = "two_factor_since")
    var twoFactorSince: Instant? = null

    /** When the person answered the one-time offer to set up two-factor, either way. */
    @Column(name = "two_factor_offer_answered_at")
    var twoFactorOfferAnsweredAt: Instant? = null

    /** After an admin's two-factor reset, until the re-enrolment link is used. */
    @Column(name = "awaiting_reenrolment", nullable = false)
    var awaitingReenrolment: Boolean = false

    @Column(name = "locked_at")
    var lockedAt: Instant? = null

    /** An address the person asked to move to, until they confirm it from that inbox. */
    @Column(name = "pending_email")
    @ColumnTransformer(read = "lower(pending_email)", write = "lower(trim(?))")
    var pendingEmail: String? = null

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    var address: Address? = null
        internal set

    val addressId: Long?
        get() = address?.id

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var memberProfile: MemberProfile? = null
        internal set

    val personDetailsId: Long?
        get() = memberProfile?.id

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _memberships: MutableSet<Membership> = linkedSetOf()
    val memberships: Set<Membership>
        get() = _memberships

    val hasTwoFactor: Boolean
        get() = twoFactorSince != null

    /** Granted roles held without two-factor: kept, shown, and allowing nothing. */
    val dormantRoles: Set<Role>
        get() = if (hasTwoFactor) emptySet() else roles.filter { GrantedRoles.isAssignable(it) }.toSet()

    val rolesInForce: Set<Role>
        get() = roles - dormantRoles

    /** Whether the person holds a role an admin grants, dormant or not. */
    val holdsGrantedRole: Boolean
        get() = roles.any { GrantedRoles.isAssignable(it) }

    /** Every role held, inheritance included: who somebody is, such as whether they count as a member. */
    val inheritedRoles: Set<Role>
        get() = roles.flatMap { it.allInheritedRoles }.toSet()

    /** What the roles in force allow, inheritance included: what somebody may do right now. */
    val inheritedRolesInForce: Set<Role>
        get() = rolesInForce.flatMap { it.allInheritedRoles }.toSet()

    fun hasRole(role: Role): Boolean = roles.any { it == role }

    fun hasAuthority(role: Role): Boolean = inheritedRoles.any { it.matchesRole(role) }

    val authorities: Collection<GrantedAuthority>
        get() =
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

    fun replaceAddress(address: Address?) {
        this.address = address
    }

    fun replaceMemberProfile(memberProfile: MemberProfile?) {
        this.memberProfile = memberProfile
    }
}

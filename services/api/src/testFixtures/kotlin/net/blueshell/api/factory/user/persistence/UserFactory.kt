package net.blueshell.api.factory.user.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.domain.GrantedRoles
import net.blueshell.api.user.persistence.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.sql.Date
import java.time.Instant
import java.time.LocalDate

@Component
class UserFactory(
    private val passwordEncoder: PasswordEncoder,
    private val persistence: FactoryPersistenceSupport,
) {
    /**
     * A person holding [role]. Somebody holding a granted role has two-factor by default, as the
     * api requires of them; without it the role is dormant and allows nothing (api ADR-031).
     */
    fun buildUserWithRole(
        role: Role,
        enabled: Boolean = true,
        twoFactor: Boolean = GrantedRoles.isAssignable(role),
    ): User {
        val username = "user_${role.name.lowercase()}_${System.currentTimeMillis()}"
        val user =
            User(
                username = username,
                email = "$username@test.com",
                password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
                initials = "TU",
                firstName = "Test",
                lastName = role.name,
                phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
                discord = "$username#0001",
            )
        user.roles = mutableSetOf(role)
        user.enabled = enabled
        user.twoFactorSince = if (twoFactor) Instant.parse("2026-01-01T00:00:00Z") else null
        return user
    }

    fun createUserWithRole(
        role: Role,
        enabled: Boolean = true,
        twoFactor: Boolean = GrantedRoles.isAssignable(role),
    ): User = persistence.persist(buildUserWithRole(role, enabled, twoFactor))

    fun buildAddress(
        user: User,
        country: String = "NL",
        city: String = "Enschede",
        street: String = "Street",
        houseNumber: String = "1",
        zipCode: String = "1234AB",
    ): Address =
        Address(
            user = user,
            country = country,
            city = city,
            street = street,
            houseNumber = houseNumber,
            zipCode = zipCode,
        )

    fun createAddress(
        user: User,
        country: String = "NL",
        city: String = "Enschede",
        street: String = "Street",
        houseNumber: String = "1",
        zipCode: String = "1234AB",
    ): Address = persistence.persist(buildAddress(user, country, city, street, houseNumber, zipCode))

    fun buildMemberProfile(user: User): MemberProfile =
        MemberProfile(
            user = user,
            dateOfBirth = Date.valueOf("1999-05-05"),
            studentNumber = "s${System.currentTimeMillis()}",
            gender = "X",
            bhv = false,
            ehbo = false,
            nationality = "NL",
        )

    fun createMemberProfile(user: User): MemberProfile = persistence.persist(buildMemberProfile(user))

    fun buildMembership(
        user: User,
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate? = null,
        incasso: Boolean = true,
    ): Membership =
        Membership(
            user = user,
            startDate = startDate,
            endDate = endDate,
            memberType = memberType,
            incasso = incasso,
        )

    fun createMembership(
        user: User,
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate? = null,
        incasso: Boolean = true,
    ): Membership = persistence.persist(buildMembership(user, memberType, startDate, endDate, incasso))
}

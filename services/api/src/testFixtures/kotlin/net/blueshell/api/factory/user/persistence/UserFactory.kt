package net.blueshell.api.factory.user.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.sql.Date
import java.time.LocalDate

@Component
class UserFactory(
    private val passwordEncoder: PasswordEncoder,
    private val persistence: FactoryPersistenceSupport
) {
    fun buildUserWithRole(role: Role, enabled: Boolean = true): User {
        val username = "user_${role.name.lowercase()}_${System.currentTimeMillis()}"
        val user = User(
            username = username,
            email = "$username@test.com",
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
            initials = "TU",
            firstName = "Test",
            lastName = role.name,
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.roles = mutableSetOf(role)
        user.enabled = enabled
        return user
    }

    fun createUserWithRole(role: Role, enabled: Boolean = true): User {
        return persistence.persist(buildUserWithRole(role, enabled))
    }

    fun buildAddress(
        user: User,
        country: String = "NL",
        city: String = "Enschede",
        street: String = "Street",
        houseNumber: String = "1",
        zipCode: String = "1234AB"
    ): Address {
        return Address(
            user = user,
            country = country,
            city = city,
            street = street,
            houseNumber = houseNumber,
            zipCode = zipCode
        )
    }

    fun createAddress(
        user: User,
        country: String = "NL",
        city: String = "Enschede",
        street: String = "Street",
        houseNumber: String = "1",
        zipCode: String = "1234AB"
    ): Address {
        return persistence.persist(buildAddress(user, country, city, street, houseNumber, zipCode))
    }

    fun buildMemberProfile(user: User): MemberProfile {
        return MemberProfile(
            user = user,
            dateOfBirth = Date.valueOf("1999-05-05"),
            studentNumber = "s${System.currentTimeMillis()}",
            gender = "X",
            bhv = false,
            ehbo = false,
            nationality = "NL"
        )
    }

    fun createMemberProfile(user: User): MemberProfile {
        return persistence.persist(buildMemberProfile(user))
    }

    fun buildMembership(
        user: User,
        memberType: MemberType = MemberType.REGULAR
    ): Membership {
        return Membership(
            user = user,
            startDate = LocalDate.now().minusDays(30),
            endDate = null,
            memberType = memberType,
            incasso = true,
        )
    }

    fun createMembership(
        user: User,
        memberType: MemberType = MemberType.REGULAR
    ): Membership {
        return persistence.persist(buildMembership(user, memberType))
    }
}

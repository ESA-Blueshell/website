package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.model.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.sql.Date
import java.time.LocalDate
import java.util.EnumSet
import java.util.Random
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for User model test instances.
 */
@Component
class UserFactory(
    private val faker: Faker,
    private val passwordEncoder: PasswordEncoder,
    private val random: Random,
    private val addressFactory: AddressFactory
) {

    fun createBasic(): User {
        val user = User()
        user.username = faker.name().username().lowercase().replace("[^a-z0-9]".toRegex(), "")
        user.password = passwordEncoder.encode("password123")
        user.firstName = faker.name().firstName()
        user.lastName = faker.name().lastName()
        user.email = faker.internet().emailAddress().lowercase()
        user.enabled = true
        user.newsletter = faker.bool().bool()
        user.roles = EnumSet.of(Role.GUEST)
        return user
    }

    fun createFull(): User {
        val user = createBasic()
        user.prefix = faker.name().prefix()
        user.initials = generateInitials(user.firstName, user.lastName)
        user.address = addressFactory.createBasic()
        user.phoneNumber = faker.phoneNumber().phoneNumber()
        user.studentNumber = faker.number().numberBetween(1000000, 9999999).toString()
        user.dateOfBirth = Date.valueOf(LocalDate.now().minusYears(faker.number().numberBetween(18, 30).toLong()))
        user.discord = faker.name().username() + "#" + faker.number().numberBetween(1000, 9999)
        user.steamid = faker.number().randomNumber(17, true).toString()
        user.consentPrivacy = true
        user.consentGdpr = true
        user.gender = faker.options().option("Male", "Female", "Other")
        user.photoConsent = faker.bool().bool()
        user.nationality = faker.nation().nationality()
        user.ehbo = faker.bool().bool()
        user.bhv = faker.bool().bool()
        user.study = faker.educator().course()
        user.startStudyYear = faker.number().numberBetween(2018, 2023).toLong()

        if (faker.bool().bool()) {
            user.roles.add(Role.MEMBER)
        }
        if (faker.bool().bool()) {
            user.roles.add(faker.options().option(Role.COMMITTEE, Role.BOARD))
        }

        return user
    }

    fun createWithCustomizations(customizer: Consumer<User>): User {
        val user = createFull()
        customizer.accept(user)
        return user
    }

    fun createWithRole(role: Role): User {
        return createWithCustomizations { user ->
            user.roles.clear()
            user.roles.add(role)
        }
    }

    fun createAdmin(): User = createWithRole(Role.ADMIN)

    fun createBoardMember(): User = createWithRole(Role.BOARD)

    fun createCommitteeMember(): User = createWithRole(Role.COMMITTEE)

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private fun generateInitials(firstName: String?, lastName: String?): String {
        val firstInitial = firstName?.firstOrNull() ?: ' '
        val lastInitial = lastName?.firstOrNull() ?: ' '
        return "$firstInitial. $lastInitial".uppercase()
    }

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}

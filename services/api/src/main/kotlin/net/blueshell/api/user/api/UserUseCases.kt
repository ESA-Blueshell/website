package net.blueshell.api.user.api

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import net.blueshell.api.user.domain.BoardUserData
import net.blueshell.api.user.domain.NewUserData
import net.blueshell.api.user.domain.SelfUserData
import net.blueshell.api.user.domain.UpsertMemberProfileData
import net.blueshell.api.user.domain.UserQuery
import net.blueshell.api.user.domain.UserRegistration
import net.blueshell.api.user.domain.UserUniqueness

/**
 * User writes. Each of the three write shapes asserts that it does not collide
 * with an existing account before it is applied, and registration additionally
 * asserts the rules that only apply when the applicant registers themselves.
 */
@Service
class UserUseCases(
    private val service: UserService,
    private val erasure: UserErasureService,
    private val passwordEncoder: PasswordEncoder,
    private val validator: Validator,
) {
    fun findByQuery(filter: UserQuery, pageable: Pageable): Page<User> = service.findByQuery(filter, pageable)

    fun findById(userId: Long): User = service.findById(userId)

    fun findDeleted(pageable: Pageable): Page<DeletedUser> = erasure.findDeletedUsers(pageable)

    fun delete(userId: Long) = erasure.deleteUser(userId)

    fun restore(userId: Long) = erasure.restoreDeletedUser(userId)

    fun toggleRole(userId: Long, role: Role): User = service.toggleRole(userId, role)

    /**
     * [isBoard] is a property of the route, not of the applicant: a board member
     * creating an account supplies no password, so one is generated for them and
     * the public-registration rules do not apply.
     */
    fun create(data: NewUserData, isBoard: Boolean): User {
        validate(
            UserRegistration(
                isBoard = isBoard,
                username = data.username,
                email = data.email,
                discord = data.discord,
                phoneNumber = data.phoneNumber,
                password = data.password,
                consentPrivacy = data.consentPrivacy,
            )
        )
        val user = User(
            username = data.username,
            email = data.email,
            initials = data.initials,
            firstName = data.firstName,
            prefix = data.prefix,
            lastName = data.lastName,
            discord = data.discord,
            phoneNumber = data.phoneNumber,
            newsletter = data.newsletter,
            consentPrivacy = data.consentPrivacy,
            photoConsent = data.photoConsent,
            password = encode(
                if (isBoard) {
                    MappingUtil.generateRandomString()
                } else {
                    requireNotNull(data.password) { "Password is required for public user registration" }
                }
            ),
        ).apply {
            data.memberProfile?.let { replaceMemberProfile(it.toEntity(this)) }
        }
        return service.create(user)
    }

    fun boardUpdate(id: Long, data: BoardUserData): User {
        validate(
            UserUniqueness(
                subjectId = id,
                username = data.username,
                email = data.email,
                discord = data.discord,
                phoneNumber = data.phoneNumber,
            )
        )
        val user = service.findById(id).apply {
            username = data.username
            email = data.email
            discord = data.discord
            phoneNumber = data.phoneNumber
            newsletter = data.newsletter
            photoConsent = data.photoConsent
            initials = data.initials
            firstName = data.firstName
            prefix = data.prefix
            lastName = data.lastName
            version = data.version
            data.memberProfile?.upsertInto(this)
        }
        return service.update(user)
    }

    fun update(id: Long, data: SelfUserData): User {
        // Username and email are absent from the self-service shape, so only the
        // two fields it can change are checked.
        validate(UserUniqueness(subjectId = id, discord = data.discord, phoneNumber = data.phoneNumber))
        val user = service.findById(id).apply {
            discord = data.discord
            phoneNumber = data.phoneNumber
            newsletter = data.newsletter
            photoConsent = data.photoConsent
            version = data.version
            data.memberProfile?.upsertInto(this)
        }
        return service.update(user)
    }

    private fun encode(raw: String): String =
        requireNotNull(passwordEncoder.encode(raw)) { "PasswordEncoder returned null hash" }

    private fun validate(candidate: Any) {
        val violations = validator.validate(candidate)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
}

internal fun UpsertMemberProfileData.toEntity(user: User): MemberProfile =
    MemberProfile(
        user = user,
        dateOfBirth = dateOfBirth,
        studentNumber = studentNumber,
        gender = gender,
        nationality = nationality,
        bhv = bhv,
        ehbo = ehbo,
        nameOnTeamPages = nameOnTeamPages
    )

internal fun UpsertMemberProfileData.upsertInto(user: User) {
    val existing = user.memberProfile
    if (existing == null) {
        user.replaceMemberProfile(toEntity(user))
        return
    }

    existing.dateOfBirth = dateOfBirth
    existing.studentNumber = studentNumber
    existing.gender = gender
    existing.nationality = nationality
    existing.bhv = bhv
    existing.ehbo = ehbo
    existing.nameOnTeamPages = nameOnTeamPages
    // The board and self-service payloads both require a version, so this only
    // skips the optimistic check for the signup routes, where the token holder is
    // the only writer. Force-unwrapping here would answer them with a 500.
    version?.let { existing.version = it }
}

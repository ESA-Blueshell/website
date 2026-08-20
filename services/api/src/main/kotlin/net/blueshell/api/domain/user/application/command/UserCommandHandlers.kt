package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.erasure.UserErasureService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.persistence.DeletedUser
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.auth.application.SignupTokenService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.util.MappingUtil
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Component
class CreateUserHandler(
    private val service: UserService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateUserCommand, User> {
    override val commandType = CreateUserCommand::class

    override fun handle(command: CreateUserCommand): User {
        var user = User(
            username = command.username,
            email = command.email,
            initials = command.initials,
            firstName = command.firstName,
            prefix = command.prefix,
            lastName = command.lastName,
            discord = command.discord,
            phoneNumber = command.phoneNumber,
            newsletter = command.newsletter,
            consentPrivacy = command.consentPrivacy,
            photoConsent = command.photoConsent,
            password = if (command.isBoard) {
                requireNotNull(passwordEncoder.encode(MappingUtil.generateRandomString())) { "PasswordEncoder returned null hash" }
            } else {
                requireNotNull(
                    passwordEncoder.encode(
                        requireNotNull(command.password) { "Password is required for public user registration" }
                    )
                ) { "PasswordEncoder returned null hash" }
            },
        ).apply {
            command.memberProfile?.let { replaceMemberProfile(it.toEntity(this)) }
        }


        user = service.create(user)
        return user
    }
}

@Component
class BoardUpdateUserHandler(
    private val service: UserService
) : CommandHandler<BoardUpdateUserCommand, User> {
    override val commandType = BoardUpdateUserCommand::class

    override fun handle(command: BoardUpdateUserCommand): User {
        var user = service.findById(command.id).apply {
            username = command.username
            email = command.email
            discord = command.discord
            phoneNumber = command.phoneNumber
            newsletter = command.newsletter
            photoConsent = command.photoConsent
            initials = command.initials
            firstName = command.firstName
            prefix = command.prefix
            lastName = command.lastName
            version = command.version
            command.memberProfile?.upsertInto(this)
        }
        user = service.update(user)
        return user
    }
}

@Component
class UpdateUserHandler(
    private val service: UserService,
) : CommandHandler<UpdateUserCommand, User> {
    override val commandType = UpdateUserCommand::class

    override fun handle(command: UpdateUserCommand): User {
        var user = service.findById(command.id).apply {
            discord = command.discord
            phoneNumber = command.phoneNumber
            newsletter = command.newsletter
            photoConsent = command.photoConsent
            version = command.version
            command.memberProfile?.upsertInto(this)
        }
        user = service.update(user)
        return user
    }
}

@Component
class UpdateSignupDetailsHandler(
    private val users: UserService,
    private val signupTokens: SignupTokenService
) : CommandHandler<UpdateSignupDetailsCommand, Unit> {
    override val commandType = UpdateSignupDetailsCommand::class

    // Transactional so the account resolved from the token stays managed.
    @Transactional
    override fun handle(command: UpdateSignupDetailsCommand) {
        val account = signupTokens.resolveAccount(command.signupToken)
        if (account.user.enabled) {
            throw AccessDeniedException("A confirmed account changes its details under a session")
        }

        // Uniqueness is checked here rather than through @UniqueUserCommand: the
        // account is only known once the token resolves, and until then the
        // applicant's own username reads as a conflict with itself.
        refuseIfTaken(users.existsByUsernameAndIdNot(command.username, account.id), "That username is already in use")
        refuseIfTaken(users.existsByDiscordAndIdNot(command.discord, account.id), "That Discord name is already in use")
        refuseIfTaken(
            users.existsByPhoneNumberAndIdNot(command.phoneNumber, account.id),
            "That phone number is already in use"
        )

        users.update(
            account.user.apply {
                username = command.username
                initials = command.initials
                firstName = command.firstName
                prefix = command.prefix
                lastName = command.lastName
                discord = command.discord
                phoneNumber = command.phoneNumber
                newsletter = command.newsletter
                photoConsent = command.photoConsent
                command.memberProfile?.upsertInto(this)
            }
        )
    }

    private fun refuseIfTaken(taken: Boolean, message: String) {
        if (taken) throw ResponseStatusException(HttpStatus.CONFLICT, message)
    }
}

@Component
class FindUsersHandler(
    private val service: UserService
) : CommandHandler<FindUsersCommand, Page<User>> {
    override val commandType = FindUsersCommand::class

    override fun handle(command: FindUsersCommand): Page<User> {
        return service.findByQuery(command.filter, command.pageable)
    }
}

@Component
class FindUserByIdHandler(
    private val service: UserService
) : CommandHandler<FindUserByIdCommand, User> {
    override val commandType = FindUserByIdCommand::class

    override fun handle(command: FindUserByIdCommand): User {
        return service.findById(command.userId)
    }
}

@Component
class DeleteUserByIdHandler(
    private val erasure: UserErasureService
) : CommandHandler<DeleteUserByIdCommand, Unit> {
    override val commandType = DeleteUserByIdCommand::class

    override fun handle(command: DeleteUserByIdCommand) {
        erasure.deleteUser(command.userId)
    }
}

@Component
class FindDeletedUsersHandler(
    private val erasure: UserErasureService
) : CommandHandler<FindDeletedUsersCommand, Page<DeletedUser>> {
    override val commandType = FindDeletedUsersCommand::class

    override fun handle(command: FindDeletedUsersCommand): Page<DeletedUser> {
        return erasure.findDeletedUsers(command.pageable)
    }
}

@Component
class RestoreDeletedUserByIdHandler(
    private val erasure: UserErasureService
) : CommandHandler<RestoreDeletedUserByIdCommand, Unit> {
    override val commandType = RestoreDeletedUserByIdCommand::class

    override fun handle(command: RestoreDeletedUserByIdCommand) {
        erasure.restoreDeletedUser(command.userId)
    }
}

@Component
class ToggleUserRoleHandler(
    private val service: UserService
) : CommandHandler<ToggleUserRoleCommand, User> {
    override val commandType = ToggleUserRoleCommand::class

    override fun handle(command: ToggleUserRoleCommand): User {
        return service.toggleRole(command.userId, command.role)
    }
}

private fun UpsertMemberProfileData.toEntity(user: User): MemberProfile =
    MemberProfile(
        user = user,
        dateOfBirth = dateOfBirth,
        studentNumber = studentNumber,
        gender = gender,
        nationality = nationality,
        bhv = bhv,
        ehbo = ehbo
    )

private fun UpsertMemberProfileData.upsertInto(user: User) {
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
    // The board and self-service payloads both require a version, so this only
    // skips the optimistic check for the signup routes, where the token holder is
    // the only writer. Force-unwrapping here would answer them with a 500.
    version?.let { existing.version = it }
}

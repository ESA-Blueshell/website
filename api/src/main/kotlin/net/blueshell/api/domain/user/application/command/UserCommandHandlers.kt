package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.util.MappingUtil
import net.blueshell.api.shared.validation.DatabaseValidationErrors
import org.springframework.data.domain.Page
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CreateUserHandler(
    private val service: UserService,
    private val addresses: AddressService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateUserCommand, User> {
    override val commandType = CreateUserCommand::class

    override fun handle(command: CreateUserCommand): User {
        validateCreateUser(command, service)
        var user = User()
        applyIdentityFields(
            user,
            command.username,
            command.email,
            command.initials,
            command.firstName,
            command.prefix,
            command.lastName
        )
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber
        user.dateOfBirth = command.dateOfBirth
        user.nationality = command.nationality
        user.photoConsent = command.photoConsent
        user.bhv = command.bhv
        user.ehbo = command.ehbo
        user.newsletter = command.newsletter
        user.gender = command.gender
        user.studentNumber = command.studentNumber
        command.addressId?.let { user.address = addresses.findById(it) }

        if (command.isBoard) {
            user.enabled = command.enabled
            user.roles = command.roles.toMutableSet()
        }

        user.password = if (command.isBoard) {
            passwordEncoder.encode(MappingUtil.generateRandomString())
        } else {
            passwordEncoder.encode(command.password)
        }
        user = service.create(user)
        return user
    }
}

@Component
class CreateGuestUserHandler(
    private val service: UserService,
    private val addresses: AddressService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateGuestUserCommand, User> {
    override val commandType = CreateGuestUserCommand::class

    override fun handle(command: CreateGuestUserCommand): User {
        validateCreateGuestUser(command, service)
        var user = User()
        applyIdentityFields(
            user,
            command.username,
            command.email,
            command.initials,
            command.firstName,
            command.prefix,
            command.lastName
        )
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber
        user.newsletter = command.newsletter
        command.addressId?.let { user.address = addresses.findById(it) }
        user.password = passwordEncoder.encode(command.password)
        user = service.create(user)
        return user
    }
}

@Component
class UpdateGuestUserHandler(
    private val service: UserService
) : CommandHandler<UpdateGuestUserCommand, User> {
    override val commandType = UpdateGuestUserCommand::class

    override fun handle(command: UpdateGuestUserCommand): User {
        validateUpdateGuestUser(command, service)
        var user = service.findById(command.id)
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber
        user.newsletter = command.newsletter
        command.version?.let { user.version = it }
        user = service.update(user)
        return user
    }
}

@Component
class UpdateUserHandler(
    private val service: UserService,
    private val addresses: AddressService
) : CommandHandler<UpdateUserCommand, User> {
    override val commandType = UpdateUserCommand::class

    override fun handle(command: UpdateUserCommand): User {
        validateUpdateUser(command, service)
        var user = service.findById(command.id)
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber
        user.dateOfBirth = command.dateOfBirth
        user.nationality = command.nationality
        user.photoConsent = command.photoConsent
        user.bhv = command.bhv
        user.ehbo = command.ehbo
        user.newsletter = command.newsletter
        user.gender = command.gender
        user.studentNumber = command.studentNumber
        command.addressId?.let { user.address = addresses.findById(it) }
        command.version?.let { user.version = it }

        if (command.isBoard) {
            user.enabled = command.enabled
            user.roles = command.roles.toMutableSet()
            applyIdentityFields(
                user,
                command.username,
                command.email,
                command.initials,
                command.firstName,
                command.prefix,
                command.lastName
            )
        }
        user = service.update(user)
        return user
    }
}

private fun validateCreateUser(command: CreateUserCommand, users: UserService) {
    val errors = DatabaseValidationErrors(CreateUserCommand::class.simpleName ?: "CreateUserCommand")
    val id = null
    validateUserUniqueness(errors, users, id, command.username, command.email, command.discord, command.phoneNumber)
    errors.throwIfAny()
}

private fun validateCreateGuestUser(command: CreateGuestUserCommand, users: UserService) {
    val errors = DatabaseValidationErrors(CreateGuestUserCommand::class.simpleName ?: "CreateGuestUserCommand")
    val id = null
    validateUserUniqueness(errors, users, id, command.username, command.email, command.discord, command.phoneNumber)
    errors.throwIfAny()
}

private fun validateUpdateGuestUser(command: UpdateGuestUserCommand, users: UserService) {
    val errors = DatabaseValidationErrors(UpdateGuestUserCommand::class.simpleName ?: "UpdateGuestUserCommand")
    val id = command.id
    validateUserUniqueness(errors, users, id, null, null, command.discord, command.phoneNumber)
    errors.throwIfAny()
}

private fun validateUpdateUser(command: UpdateUserCommand, users: UserService) {
    val errors = DatabaseValidationErrors(UpdateUserCommand::class.simpleName ?: "UpdateUserCommand")
    val id = command.id
    if (command.isBoard) {
        validateUserUniqueness(
            errors,
            users,
            id,
            command.username,
            command.email,
            command.discord,
            command.phoneNumber
        )
    } else {
        validateUserUniqueness(errors, users, id, null, null, command.discord, command.phoneNumber)
    }
    errors.throwIfAny()
}

private fun validateUserUniqueness(
    errors: DatabaseValidationErrors,
    users: UserService,
    id: Long?,
    username: String?,
    email: String?,
    discord: String?,
    phoneNumber: String?
) {
    if (!username.isNullOrBlank()) {
        val taken = if (id == null) users.existsByUsername(username) else users.existsByUsernameAndIdNot(username, id)
        if (taken) errors.reject("username", username, "Username is taken.", "UniqueUser")
    }

    if (!email.isNullOrBlank()) {
        val taken = if (id == null) users.existsByEmail(email) else users.existsByEmailAndIdNot(email, id)
        if (taken) errors.reject("email", email, "Email is taken.", "UniqueUser")
    }

    if (!discord.isNullOrBlank()) {
        val taken = if (id == null) users.existsByDiscord(discord) else users.existsByDiscordAndIdNot(discord, id)
        if (taken) errors.reject("discord", discord, "Discord is taken.", "UniqueUser")
    }

    if (!phoneNumber.isNullOrBlank()) {
        val taken = if (id == null) users.existsByPhoneNumber(phoneNumber) else users.existsByPhoneNumberAndIdNot(
            phoneNumber,
            id
        )
        if (taken) errors.reject("phoneNumber", phoneNumber, "Phone number is taken.", "UniqueUser")
    }
}

@Component
class FindUsersHandler(
    private val service: UserService
) : CommandHandler<FindUsersCommand, Page<User>> {
    override val commandType = FindUsersCommand::class

    override fun handle(command: FindUsersCommand): Page<User> {
        return service.findByFilter(command.filter, command.pageable)
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
    private val service: UserService
) : CommandHandler<DeleteUserByIdCommand, Unit> {
    override val commandType = DeleteUserByIdCommand::class

    override fun handle(command: DeleteUserByIdCommand) {
        service.deleteById(command.userId)
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

private fun applyIdentityFields(
    user: User,
    username: String?,
    email: String?,
    initials: String?,
    firstName: String?,
    prefix: String?,
    lastName: String?
) {
    username?.let { user.username = it }
    email?.let { user.email = it }
    initials?.let { user.initials = it }
    firstName?.let { user.firstName = it }
    prefix?.let { user.prefix = it }
    lastName?.let { user.lastName = it }
}

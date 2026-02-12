package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.util.MappingUtil
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

package net.blueshell.api.domain.user.application.command

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.CreateGuestUserCommand
import net.blueshell.api.domain.user.command.CreateUserCommand
import net.blueshell.api.domain.user.command.DeleteUserByIdCommand
import net.blueshell.api.domain.user.command.FindUserByIdCommand
import net.blueshell.api.domain.user.command.FindUsersCommand
import net.blueshell.api.domain.user.command.ToggleUserRoleCommand
import net.blueshell.api.domain.user.command.UpdateGuestUserCommand
import net.blueshell.api.domain.user.command.UpdateUserCommand
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.web.mapping.asEntity
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.validation.group.Administration
import net.blueshell.api.shared.validation.group.Creation
import net.blueshell.api.shared.validation.group.Update
import org.springframework.data.domain.Page
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CreateUserHandler(
    private val service: UserService,
    private val validator: Validator,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateUserCommand, User> {
    override val commandType = CreateUserCommand::class

    override fun handle(command: CreateUserCommand): User {
        val groups: Array<Class<*>> = if (command.isBoard) {
            arrayOf(Administration::class.java)
        } else {
            arrayOf(Creation::class.java)
        }

        val violations = validator.validate(command.dto, *groups)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }

        var user = command.dto.asEntity(User(), passwordEncoder)
        user = service.create(user)
        return user
    }
}

@Component
class CreateGuestUserHandler(
    private val service: UserService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateGuestUserCommand, User> {
    override val commandType = CreateGuestUserCommand::class

    override fun handle(command: CreateGuestUserCommand): User {
        var user = command.dto.asEntity(User(), passwordEncoder)
        user = service.create(user)
        return user
    }
}

@Component
class UpdateGuestUserHandler(
    private val service: UserService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<UpdateGuestUserCommand, User> {
    override val commandType = UpdateGuestUserCommand::class

    override fun handle(command: UpdateGuestUserCommand): User {
        var user = service.findById(command.id)
        command.dto.asEntity(user, passwordEncoder)
        user = service.update(user)
        return user
    }
}

@Component
class UpdateUserHandler(
    private val service: UserService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<UpdateUserCommand, User> {
    override val commandType = UpdateUserCommand::class

    override fun handle(command: UpdateUserCommand): User {
        var user = service.findById(command.id)
        command.dto.asEntity(user, passwordEncoder)
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

package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.erasure.UserErasureService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.persistence.DeletedUser
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.util.MappingUtil
import org.springframework.data.domain.Page
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

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
            version = command.version
            command.memberProfile?.upsertInto(this)
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
    existing.version = version!!
}

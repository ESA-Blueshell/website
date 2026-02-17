package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.BoardUpdateUserCommand
import net.blueshell.api.domain.user.command.CreateUserCommand
import net.blueshell.api.domain.user.command.DeleteUserByIdCommand
import net.blueshell.api.domain.user.command.FindUserByIdCommand
import net.blueshell.api.domain.user.command.FindUsersCommand
import net.blueshell.api.domain.user.command.ToggleUserRoleCommand
import net.blueshell.api.domain.user.command.UpdateUserCommand
import net.blueshell.api.domain.user.command.UpsertMemberProfileData
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.util.MappingUtil
import org.springframework.data.domain.Page
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.sql.Date

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
            password = if (command.isBoard) {
                passwordEncoder.encode(MappingUtil.generateRandomString())
            } else {
                passwordEncoder.encode(command.password)
            },
        )
        command.memberProfile?.let { payload ->
            user.replaceMemberProfile(payload.toEntity(user))
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
            command.memberProfile?.let { payload ->
                val profile = memberProfile
                if (profile == null) {
                    replaceMemberProfile(payload.toEntity(this))
                } else {
                    profile.dateOfBirth = Date.valueOf(payload.dateOfBirth)
                    profile.studentNumber = payload.studentNumber
                    profile.gender = payload.gender
                    profile.photoConsent = payload.photoConsent
                    profile.nationality = payload.nationality
                    profile.bhv = payload.bhv
                    profile.ehbo = payload.ehbo
                    payload.version?.let { profile.version = it }
                }
            }
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
            command.memberProfile?.let { payload ->
                replaceMemberProfile(payload.toEntity(this))
                val profile = memberProfile
                if (profile == null) {
                    replaceMemberProfile(payload.toEntity(this))
                } else {
                    profile.dateOfBirth = Date.valueOf(payload.dateOfBirth)
                    profile.studentNumber = payload.studentNumber
                    profile.gender = payload.gender
                    profile.photoConsent = payload.photoConsent
                    profile.nationality = payload.nationality
                    profile.bhv = payload.bhv
                    profile.ehbo = payload.ehbo
                    payload.version?.let { profile.version = it }
                }
            }
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

private fun UpsertMemberProfileData.toEntity(user: User): MemberProfile =
    MemberProfile(
        user = user,
        dateOfBirth = Date.valueOf(dateOfBirth),
        studentNumber = studentNumber,
        gender = gender,
        photoConsent = photoConsent,
        nationality = nationality,
        bhv = bhv,
        ehbo = ehbo
    ).also { profile ->
        version?.let { profile.version = it }
    }

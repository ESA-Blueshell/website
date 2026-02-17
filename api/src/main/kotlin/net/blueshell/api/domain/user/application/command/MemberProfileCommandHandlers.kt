package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.CreateMemberProfileCommand
import net.blueshell.api.domain.user.command.FindMemberProfileByUserIdCommand
import net.blueshell.api.domain.user.command.UpdateMemberProfileCommand
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.sql.Date

@Component
class CreateMemberProfileHandler(
    private val users: UserService
) : CommandHandler<CreateMemberProfileCommand, MemberProfile> {
    override val commandType = CreateMemberProfileCommand::class

    override fun handle(command: CreateMemberProfileCommand): MemberProfile {
        val user = users.findById(command.userId)
        val profile = MemberProfile(
            user = user,
            dateOfBirth = Date.valueOf(command.dateOfBirth),
            studentNumber = command.studentNumber,
            gender = command.gender,
            photoConsent = command.photoConsent,
            nationality = command.nationality,
            bhv = command.bhv,
            ehbo = command.ehbo
        )
        user.replaceMemberProfile(profile)
        val updated = users.update(user)
        return checkNotNull(updated.memberProfile) { "Member profile was not linked to user ${user.id}" }
    }
}

@Component
class UpdateMemberProfileHandler(
    private val users: UserService
) : CommandHandler<UpdateMemberProfileCommand, MemberProfile> {
    override val commandType = UpdateMemberProfileCommand::class

    override fun handle(command: UpdateMemberProfileCommand): MemberProfile {
        val user = users.findById(command.userId)
        val profile = user.memberProfile ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "MemberProfile not found for user ${command.userId}"
        )

        profile.dateOfBirth = Date.valueOf(command.dateOfBirth)
        profile.studentNumber = command.studentNumber
        profile.gender = command.gender
        profile.photoConsent = command.photoConsent
        profile.nationality = command.nationality
        profile.bhv = command.bhv
        profile.ehbo = command.ehbo
        profile.version = command.version

        val updated = users.update(user)
        return checkNotNull(updated.memberProfile) { "Member profile was not linked to user ${user.id}" }
    }
}

@Component
class FindMemberProfileByUserIdHandler(
    private val users: UserService
) : CommandHandler<FindMemberProfileByUserIdCommand, MemberProfile> {
    override val commandType = FindMemberProfileByUserIdCommand::class

    override fun handle(command: FindMemberProfileByUserIdCommand): MemberProfile {
        val user = users.findById(command.userId)
        return user.memberProfile ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "MemberProfile not found for user ${command.userId}"
        )
    }
}

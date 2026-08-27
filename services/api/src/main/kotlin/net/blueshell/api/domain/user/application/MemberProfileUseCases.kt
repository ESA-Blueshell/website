package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.persistence.MemberProfile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.Date

/** A member profile is one-per-user, created through the user that owns it. */
@Service
class MemberProfileUseCases(
    private val memberProfileService: MemberProfileService,
    private val users: UserService,
) {
    fun create(
        userId: Long,
        dateOfBirth: Date,
        studentNumber: String?,
        gender: String?,
        nationality: String,
        bhv: Boolean,
        ehbo: Boolean,
        nameOnTeamPages: Boolean = false,
    ): MemberProfile {
        val user = users.findById(userId)
        if (user.memberProfile != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MemberProfile already exists for user $userId")
        }
        user.replaceMemberProfile(
            MemberProfile(
                user = user,
                dateOfBirth = dateOfBirth,
                studentNumber = studentNumber,
                gender = gender,
                nationality = nationality,
                bhv = bhv,
                ehbo = ehbo,
                nameOnTeamPages = nameOnTeamPages,
            ),
        )
        val updated = users.update(user)
        return checkNotNull(updated.memberProfile) { "Member profile was not linked to user ${user.id}" }
    }

    fun update(
        userId: Long,
        dateOfBirth: Date,
        studentNumber: String?,
        gender: String?,
        nationality: String,
        bhv: Boolean,
        ehbo: Boolean,
        nameOnTeamPages: Boolean = false,
        version: Long,
    ): MemberProfile {
        val profile = memberProfileService.findById(userId).apply {
            this.dateOfBirth = dateOfBirth
            this.studentNumber = studentNumber
            this.gender = gender
            this.nationality = nationality
            this.bhv = bhv
            this.ehbo = ehbo
            this.nameOnTeamPages = nameOnTeamPages
            this.version = version
        }
        return memberProfileService.update(profile)
    }

    fun findByUserId(userId: Long): MemberProfile =
        users.findById(userId).memberProfile
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "MemberProfile not found for user $userId")
}

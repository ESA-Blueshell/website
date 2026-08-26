package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.shared.command.Command
import java.sql.Date

data class CreateMemberProfileCommand(
    val userId: Long,
    val dateOfBirth: Date,
    val studentNumber: String?,
    val gender: String?,
    val nationality: String,
    val bhv: Boolean,
    val ehbo: Boolean,
    val nameOnTeamPages: Boolean = false
) : Command<MemberProfile>

data class UpdateMemberProfileCommand(
    val userId: Long,
    val dateOfBirth: Date,
    val studentNumber: String?,
    val gender: String?,
    val nationality: String,
    val bhv: Boolean,
    val ehbo: Boolean,
    val nameOnTeamPages: Boolean = false,
    val version: Long
) : Command<MemberProfile>

data class FindMemberProfileByUserIdCommand(
    val userId: Long
) : Command<MemberProfile>

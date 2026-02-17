package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.shared.command.Command
import java.time.LocalDate

data class CreateMemberProfileCommand(
    val userId: Long,
    val dateOfBirth: LocalDate,
    val studentNumber: String,
    val gender: String,
    val photoConsent: Boolean,
    val nationality: String,
    val bhv: Boolean,
    val ehbo: Boolean
) : Command<MemberProfile>

data class UpdateMemberProfileCommand(
    val id: Long,
    val userId: Long,
    val dateOfBirth: LocalDate,
    val studentNumber: String,
    val gender: String,
    val photoConsent: Boolean,
    val nationality: String,
    val bhv: Boolean,
    val ehbo: Boolean,
    val version: Long
) : Command<MemberProfile>

data class FindMemberProfileByUserIdCommand(
    val userId: Long
) : Command<MemberProfile>

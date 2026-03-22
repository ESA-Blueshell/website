package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.CreateMemberProfileCommand
import net.blueshell.api.domain.user.command.UpdateMemberProfileCommand
import net.blueshell.api.domain.user.command.UpsertMemberProfileData
import net.blueshell.api.domain.user.web.dto.request.CreateMemberProfileRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateMemberProfileRequest
import net.blueshell.api.domain.user.web.dto.request.UpsertMemberProfileRequest
import java.sql.Date

fun CreateMemberProfileRequest.asCommand(): CreateMemberProfileCommand =
    CreateMemberProfileCommand(
        userId = this.userId!!,
        dateOfBirth = Date.valueOf(this.dateOfBirth!!),
        studentNumber = this.studentNumber!!,
        gender = this.gender,
        nationality = this.nationality!!,
        bhv = this.bhv!!,
        ehbo = this.ehbo!!
    )

fun UpdateMemberProfileRequest.asCommand(userId: Long): UpdateMemberProfileCommand =
    UpdateMemberProfileCommand(
        userId = userId,
        dateOfBirth = Date.valueOf(this.dateOfBirth!!),
        studentNumber = this.studentNumber!!,
        gender = this.gender,
        nationality = this.nationality!!,
        bhv = this.bhv!!,
        ehbo = this.ehbo!!,
        version = this.version!!
    )


fun UpsertMemberProfileRequest.asCommandData(): UpsertMemberProfileData =
    UpsertMemberProfileData(
        dateOfBirth = Date.valueOf(this.dateOfBirth!!),
        studentNumber = this.studentNumber!!,
        gender = this.gender,
        nationality = this.nationality!!,
        bhv = this.bhv!!,
        ehbo = this.ehbo!!,
        version = this.version
    )

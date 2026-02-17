package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.CreateMemberProfileCommand
import net.blueshell.api.domain.user.command.UpdateMemberProfileCommand
import net.blueshell.api.domain.user.web.dto.request.CreateMemberProfileRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateMemberProfileRequest

fun CreateMemberProfileRequest.asCommand(): CreateMemberProfileCommand =
    CreateMemberProfileCommand(
        userId = this.userId!!,
        dateOfBirth = this.dateOfBirth!!,
        studentNumber = this.studentNumber!!,
        gender = this.gender!!,
        photoConsent = this.photoConsent!!,
        nationality = this.nationality!!,
        bhv = this.bhv!!,
        ehbo = this.ehbo!!
    )

fun UpdateMemberProfileRequest.asCommand(userId: Long): UpdateMemberProfileCommand =
    UpdateMemberProfileCommand(
        userId = userId,
        dateOfBirth = this.dateOfBirth!!,
        studentNumber = this.studentNumber!!,
        gender = this.gender!!,
        photoConsent = this.photoConsent!!,
        nationality = this.nationality!!,
        bhv = this.bhv!!,
        ehbo = this.ehbo!!,
        version = this.version!!
    )

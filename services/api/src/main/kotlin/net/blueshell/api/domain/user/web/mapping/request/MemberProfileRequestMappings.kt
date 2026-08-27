package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.application.UpsertMemberProfileData
import net.blueshell.api.domain.user.web.dto.request.UpsertMemberProfileRequest
import java.sql.Date

fun UpsertMemberProfileRequest.asCommandData(): UpsertMemberProfileData =
    UpsertMemberProfileData(
        dateOfBirth = Date.valueOf(this.dateOfBirth!!),
        studentNumber = this.studentNumber,
        gender = this.gender,
        nationality = this.nationality!!,
        bhv = this.bhv!!,
        ehbo = this.ehbo!!,
        nameOnTeamPages = this.nameOnTeamPages,
        version = this.version
    )

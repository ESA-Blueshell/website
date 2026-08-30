package net.blueshell.api.user.web

import net.blueshell.api.user.api.UpsertMemberProfileData
import java.sql.Date

fun UpsertMemberProfileRequest.asCommandData(): UpsertMemberProfileData =
    UpsertMemberProfileData(
        dateOfBirth = Date.valueOf(this.dateOfBirth),
        studentNumber = this.studentNumber,
        gender = this.gender,
        nationality = this.nationality,
        bhv = this.bhv,
        ehbo = this.ehbo,
        nameOnTeamPages = this.nameOnTeamPages,
        version = this.version
    )

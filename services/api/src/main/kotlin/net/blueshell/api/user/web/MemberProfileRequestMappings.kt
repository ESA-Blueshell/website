package net.blueshell.api.user.web

import net.blueshell.api.user.api.UpsertMemberProfileData
import java.sql.Date

fun UpsertMemberProfileRequest.asCommandData(): UpsertMemberProfileData =
    UpsertMemberProfileData(
        dateOfBirth = this.dateOfBirth?.let(Date::valueOf),
        studentNumber = this.studentNumber,
        gender = this.gender,
        nationality = this.nationality,
        bhv = this.bhv,
        ehbo = this.ehbo,
        nameOnRosters = this.nameOnRosters,
        version = this.version,
    )

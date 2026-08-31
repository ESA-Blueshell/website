package net.blueshell.api.user.web

import net.blueshell.api.user.persistence.MemberProfile

fun MemberProfile.asResponse(): MemberProfileResponse =
    MemberProfileResponse(
        id = this.id!!,
        userId = this.userId!!,
        dateOfBirth = this.dateOfBirth?.toLocalDate(),
        studentNumber = this.studentNumber,
        gender = this.gender,
        nationality = this.nationality,
        bhv = this.bhv,
        ehbo = this.ehbo,
        nameOnRosters = this.nameOnRosters,
        conditionsAcceptedAt = this.conditionsAcceptedAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        version = this.version
    )

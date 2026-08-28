package net.blueshell.api.user.web

import net.blueshell.api.user.persistence.Membership

fun Membership.asResponse(): MembershipResponse =
    MembershipResponse(
        userId = this.userId,
        memberType = this.memberType,
        startDate = this.startDate,
        endDate = this.endDate,
        incasso = this.incasso,
        version = this.version,
        id = this.id!!,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

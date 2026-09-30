package net.blueshell.api.user.web

import net.blueshell.api.user.domain.incassoStanding
import net.blueshell.api.user.persistence.Membership

fun Membership.asResponse(): MembershipResponse =
    MembershipResponse(
        userId = this.userId,
        memberType = this.memberType,
        startDate = this.startDate,
        endDate = this.endDate,
        incasso = this.incasso,
        incassoStanding = this.incassoStanding(),
        ibanLastFour = this.mandate?.ibanLastFour,
        version = this.version,
        id = this.id!!,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

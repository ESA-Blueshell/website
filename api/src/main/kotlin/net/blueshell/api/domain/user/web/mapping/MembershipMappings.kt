package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.web.dto.response.MembershipResponse
import tech.mappie.api.ObjectMappie

object MembershipToMembershipResponseMapper : ObjectMappie<Membership, MembershipResponse>()

fun Membership.asResponse(): MembershipResponse = MembershipToMembershipResponseMapper.map(this)

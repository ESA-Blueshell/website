package net.blueshell.api.domain.membership.web.mapping

import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.web.dto.BoardCreateMembershipRequest
import net.blueshell.api.domain.membership.web.dto.MembershipResponse
import net.blueshell.api.domain.membership.web.dto.UpdateMembershipRequest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.domain.user.persistence.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tech.mappie.api.ObjectMappie

object MembershipToMembershipResponseMapper : ObjectMappie<Membership, MembershipResponse>()

fun BoardCreateMembershipRequest.asEntity(membership: Membership = Membership()): Membership {
    membership.user = User::class.asRef(userId!!)

    if (hasAuthority(Role.BOARD)) {
        membership.startDate = startDate!!
        membership.endDate = endDate
        membership.memberType = memberType!!
        membership.incasso = incasso!!
    }

    return membership
}

fun UpdateMembershipRequest.asEntity(membership: Membership = Membership()): Membership {
    membership.user = User::class.asRef(userId!!)
    version?.let { membership.version = it }

    if (hasAuthority(Role.BOARD)) {
        startDate?.let { membership.startDate = it }
        membership.endDate = endDate
        memberType?.let { membership.memberType = it }
        incasso?.let { membership.incasso = it }
    }

    return membership
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

fun Membership.asResponse(): MembershipResponse = MembershipToMembershipResponseMapper.map(this)

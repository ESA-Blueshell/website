package net.blueshell.api.membership.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.membership.web.dto.MembershipDTO
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Konverter
interface MembershipKonverter {
    fun toDTO(membership: Membership): MembershipDTO

    fun fromDTO(dto: MembershipDTO): Membership
}

private val membershipKonverter = Konverter.get<MembershipKonverter>()

fun MembershipDTO.asEntity(membership: Membership = Membership()): Membership {
    val mapped = membershipKonverter.fromDTO(this)
    mapped.userId?.let { membership.userId = it }
    version?.let { membership.version = it }

    if (hasAuthority(Role.BOARD)) {
        mapped.startDate?.let { membership.startDate = it }
        membership.endDate = mapped.endDate
        mapped.memberType?.let { membership.memberType = it }
        membership.incasso = mapped.incasso
    }

    return membership
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

fun Membership.asDto(): MembershipDTO = membershipKonverter.toDTO(this)

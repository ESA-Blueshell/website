package net.blueshell.api.membership.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.membership.web.dto.MembershipDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.membership.persistence.Membership
import org.springframework.stereotype.Component

@Konverter
interface MembershipKonverter {
    fun toDTO(membership: Membership): MembershipDTO

    fun fromDTO(dto: MembershipDTO): Membership
}

@Component
class MembershipMapper : BaseMapper<Membership, MembershipDTO>() {
    private val konverter = konverter<MembershipKonverter>()

    override fun fromDTO(dto: MembershipDTO): Membership = fromDTO(dto, Membership())

    fun fromDTO(dto: MembershipDTO, membership: Membership): Membership {
        val mapped = konverter.fromDTO(dto)
        mapped.userId?.let { membership.userId = it }
        dto.version?.let { membership.version = it }

        if (hasAuthority(Role.BOARD)) {
            mapped.startDate?.let { membership.startDate = it }
            membership.endDate = mapped.endDate
            mapped.memberType?.let { membership.memberType = it }
            membership.incasso = mapped.incasso
        }

        return membership
    }

    override fun toDTO(membership: Membership): MembershipDTO = konverter.toDTO(membership)
}

fun Membership.asDTO(mapper: MembershipMapper): MembershipDTO = mapper.toDTO(this)

fun MembershipDTO.asEntity(mapper: MembershipMapper): Membership = mapper.fromDTO(this)

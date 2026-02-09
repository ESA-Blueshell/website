package net.blueshell.api.factory.dto

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.feature.membership.dto.MembershipDTO
import org.springframework.stereotype.Component

/**
 * Factory for MembershipDTO test instances.
 */
@Component
class MembershipDTOFactory : BaseDtoFactory<MembershipDTO>() {

    override fun targetType(): Class<MembershipDTO> = MembershipDTO::class.java

    override fun createBasic(): MembershipDTO {
        val dto = MembershipDTO()
        dto.userId = nextId()
        dto.memberType = MemberType.REGULAR
        dto.city = "Enschede"
        dto.country = "NL"
        dto.startDate = today()
        dto.endDate = null
        dto.incasso = true
        return dto
    }
}

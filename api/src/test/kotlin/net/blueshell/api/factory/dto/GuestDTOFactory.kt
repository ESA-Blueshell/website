package net.blueshell.api.factory.dto

import net.blueshell.api.feature.event.dto.GuestDTO
import org.springframework.stereotype.Component

/**
 * Factory for GuestDTO test instances.
 */
@Component
class GuestDTOFactory : BaseDtoFactory<GuestDTO>() {

    override fun targetType(): Class<GuestDTO> = GuestDTO::class.java

    override fun createBasic(): GuestDTO {
        val dto = GuestDTO()
        dto.createdAt = now()
        dto.name = "Guest ${nextId()}"
        dto.discord = "guest"
        dto.email = email("guest")
        dto.phoneNumber = "+31651319571"
        return dto
    }
}

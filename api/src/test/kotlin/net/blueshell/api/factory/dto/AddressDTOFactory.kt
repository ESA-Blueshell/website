package net.blueshell.api.factory.dto

import net.blueshell.api.feature.user.dto.AddressDTO
import org.springframework.stereotype.Component

/**
 * Factory for AddressDTO test instances.
 */
@Component
class AddressDTOFactory : BaseDtoFactory<AddressDTO>() {

    override fun targetType(): Class<AddressDTO> = AddressDTO::class.java

    override fun createBasic(): AddressDTO {
        val dto = AddressDTO()
        dto.country = "NL"
        dto.city = "Enschede"
        dto.street = "Test Street"
        dto.houseNumber = "123"
        dto.zipCode = "1234 AB"
        dto.createdAt = now()
        return dto
    }
}

package net.blueshell.api.user.web.mapper

import net.blueshell.api.user.web.dto.AddressDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.Address
import org.springframework.stereotype.Component

@Component
class AddressMapper : BaseMapper<Address, AddressDTO>() {
    override fun fromDTO(dto: AddressDTO): Address = fromDTO(dto, Address())

    fun fromDTO(dto: AddressDTO, address: Address): Address {
        address.country = dto.country
        address.city = dto.city
        address.street = dto.street
        address.houseNumber = dto.houseNumber
        address.zipCode = dto.zipCode
        dto.version?.let { address.version = it }
        return address
    }

    override fun toDTO(address: Address): AddressDTO {
        return AddressDTO(
            country = address.country,
            city = address.city,
            street = address.street,
            houseNumber = address.houseNumber,
            zipCode = address.zipCode
        ).also { dto ->
            dto.id = address.id
            dto.createdAt = address.createdAt
            dto.version = address.version
        }
    }
}

fun Address.asDTO(mapper: AddressMapper): AddressDTO = mapper.toDTO(this)

fun AddressDTO.asEntity(mapper: AddressMapper): Address = mapper.fromDTO(this)

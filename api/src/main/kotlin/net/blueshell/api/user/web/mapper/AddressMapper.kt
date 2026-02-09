package net.blueshell.api.user.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.user.web.dto.AddressDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.Address
import org.springframework.stereotype.Component

@Konverter
interface AddressKonverter {
    fun toDTO(address: Address): AddressDTO

    fun fromDTO(dto: AddressDTO): Address
}

@Component
class AddressMapper : BaseMapper<Address, AddressDTO>() {
    private val konverter = konverter<AddressKonverter>()

    override fun fromDTO(dto: AddressDTO): Address = konverter.fromDTO(dto)

    fun fromDTO(dto: AddressDTO, address: Address): Address {
        val mapped = konverter.fromDTO(dto)
        address.country = mapped.country
        address.city = mapped.city
        address.street = mapped.street
        address.houseNumber = mapped.houseNumber
        address.zipCode = mapped.zipCode
        address.version = mapped.version
        return address
    }

    override fun toDTO(address: Address): AddressDTO = konverter.toDTO(address)
}

fun Address.asDTO(mapper: AddressMapper): AddressDTO = mapper.toDTO(this)

fun AddressDTO.asEntity(mapper: AddressMapper): Address = mapper.fromDTO(this)

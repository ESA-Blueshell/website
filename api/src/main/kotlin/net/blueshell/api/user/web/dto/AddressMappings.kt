package net.blueshell.api.user.web.dto

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.user.persistence.Address

@Konverter
interface AddressKonverter {
    fun toDTO(address: Address): AddressDTO

    fun fromDTO(dto: AddressDTO): Address
}

private val addressKonverter = Konverter.get<AddressKonverter>()

fun AddressDTO.asEntity(address: Address = Address()): Address {
    val mapped = addressKonverter.fromDTO(this)
    return address.apply {
        country = mapped.country
        city = mapped.city
        street = mapped.street
        houseNumber = mapped.houseNumber
        zipCode = mapped.zipCode
        version = mapped.version
    }
}

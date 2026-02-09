package net.blueshell.api.user.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.web.dto.AddressDTO

@Konverter
interface AddressKonverter {
    fun toDTO(address: Address): AddressDTO

    fun fromDTO(dto: AddressDTO): Address
}

private val addressKonverter = Konverter.get<AddressKonverter>()

fun AddressDTO.asEntity(existing: Address? = null): Address {
    val mapped = addressKonverter.fromDTO(this)
    existing?.id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun Address.asDto(): AddressDTO = addressKonverter.toDTO(this)

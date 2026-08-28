package net.blueshell.api.user.domain

import net.blueshell.api.user.persistence.Address
import org.springframework.stereotype.Service
import net.blueshell.api.user.api.UserService

/**
 * An address belongs to its user, so creating and removing one goes through the
 * user rather than the address: the association is what is being changed.
 */
@Service
class AddressUseCases(
    private val addressService: AddressService,
    private val users: UserService,
) {
    fun create(
        userId: Long,
        country: String,
        city: String,
        street: String,
        houseNumber: String,
        zipCode: String,
    ): Address {
        val user = users.findById(userId)
        user.replaceAddress(
            Address(
                user = user,
                country = country,
                city = city,
                street = street,
                houseNumber = houseNumber,
                zipCode = zipCode,
            ),
        )
        val updated = users.update(user)
        return checkNotNull(updated.address) { "Address was not linked to user ${user.id}" }
    }

    fun update(
        id: Long,
        country: String,
        city: String,
        street: String,
        houseNumber: String,
        zipCode: String,
        version: Long,
    ): Address {
        val address = addressService.findById(id).apply {
            this.country = country
            this.city = city
            this.street = street
            this.houseNumber = houseNumber
            this.zipCode = zipCode
            this.version = version
        }
        return addressService.update(address)
    }

    fun delete(id: Long) {
        val address = addressService.findById(id)
        address.user.replaceAddress(null)
        users.update(address.user)
    }
}

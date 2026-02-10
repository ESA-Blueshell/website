package net.blueshell.api.user.persistence

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.user.web.mapping.asDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AddressModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val address = addressFactory.createBasic()
            address.country = "NL"
            address.city = "Amsterdam"
            address.street = "Damrak"
            address.houseNumber = "1A"
            address.zipCode = "1012LG"

            val found = persistAndReload(address, Address::class.java) { it.id }

            assertEquals(address.country, found.country)
            assertEquals(address.city, found.city)
            assertEquals(address.street, found.street)
            assertEquals(address.houseNumber, found.houseNumber)
            assertEquals(address.zipCode, found.zipCode)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted address`() {
            val address = persist(addressFactory.createBasic())

            val dto = address.asDto()

            assertEquals(address.id, dto.id)
            assertEquals(address.country, dto.country)
            assertEquals(address.city, dto.city)
            assertEquals(address.street, dto.street)
            assertEquals(address.houseNumber, dto.houseNumber)
            assertEquals(address.zipCode, dto.zipCode)
        }
    }
}

package net.blueshell.api.integration.model

import net.blueshell.api.model.Address
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AddressModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_column_fields() {
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
}

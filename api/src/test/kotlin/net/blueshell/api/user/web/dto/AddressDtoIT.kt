package net.blueshell.api.user.web.dto

import net.blueshell.api.factory.dto.AddressDTOFactory
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.user.application.AddressService
import net.blueshell.api.user.persistence.Address
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AddressDtoIT @Autowired constructor(
    private val addressDTOFactory: AddressDTOFactory,
    private val addressFactory: AddressFactory,
    private val addressService: AddressService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists mapped address`() {
            val dto = addressDTOFactory.createBasic()
            val address = addressFactory.createBasic()

            val mapped = dto.asEntity(address)
            val saved = addressService.create(mapped)
            flushAndClear()

            val reloaded = reload(Address::class.java, saved.id!!)

            assertThat(reloaded.country).isEqualTo(dto.country)
            assertThat(reloaded.city).isEqualTo(dto.city)
            assertThat(reloaded.street).isEqualTo(dto.street)
            assertThat(reloaded.houseNumber).isEqualTo(dto.houseNumber)
            assertThat(reloaded.zipCode).isEqualTo(dto.zipCode)
        }
    }
}

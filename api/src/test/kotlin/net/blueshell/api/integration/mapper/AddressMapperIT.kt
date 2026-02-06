package net.blueshell.api.integration.mapper

import net.blueshell.api.factory.dto.AddressDTOFactory
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.model.Address
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AddressMapperIT @Autowired constructor(
    private val addressMapper: AddressMapper,
    private val addressDTOFactory: AddressDTOFactory,
    private val addressFactory: AddressFactory
) : net.blueshell.api.integration.mapper.MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted address`() {
            val address = persist(addressFactory.createBasic())

            val dto = addressMapper.toDTO(address)

            assertThat(dto.id).isEqualTo(address.id)
            assertThat(dto.country).isEqualTo(address.country)
            assertThat(dto.city).isEqualTo(address.city)
            assertThat(dto.street).isEqualTo(address.street)
            assertThat(dto.houseNumber).isEqualTo(address.houseNumber)
            assertThat(dto.zipCode).isEqualTo(address.zipCode)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped address`() {
            val dto = addressDTOFactory.createBasic()
            val address = addressFactory.createBasic()

            val mapped = addressMapper.fromDTO(dto, address)
            val saved = persist(mapped)
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

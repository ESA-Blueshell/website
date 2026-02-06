package net.blueshell.api.mapper

import net.blueshell.api.factory.dto.AddressDTOFactory
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.model.Address
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AddressMapperIT @Autowired constructor(
    private val addressMapper: AddressMapper,
    private val addressDTOFactory: AddressDTOFactory,
    private val addressFactory: AddressFactory
) : MapperTestSupport() {
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

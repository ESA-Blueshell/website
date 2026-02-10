package net.blueshell.api.factory.dto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AddressDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var addressDTOFactory: AddressDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(addressDTOFactory)
    }
}

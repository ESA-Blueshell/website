package net.blueshell.api.factory.dto

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ApiErrorDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var apiErrorDTOFactory: ApiErrorDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(apiErrorDTOFactory)
    }
}

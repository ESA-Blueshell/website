package net.blueshell.api.factory.dto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FieldValidationErrorDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var fieldValidationErrorDTOFactory: FieldValidationErrorDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(fieldValidationErrorDTOFactory)
    }
}

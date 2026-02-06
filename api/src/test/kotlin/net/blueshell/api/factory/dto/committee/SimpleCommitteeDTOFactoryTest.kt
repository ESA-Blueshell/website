package net.blueshell.api.factory.dto.committee

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SimpleCommitteeDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var simpleCommitteeDTOFactory: SimpleCommitteeDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(simpleCommitteeDTOFactory)
    }

    @Test
    fun `creates committee with custom name`() {
        val dto = simpleCommitteeDTOFactory.createWithName("Test Committee")
        assertNoViolations(dto)
    }

    @Test
    fun `creates committee with custom details`() {
        val dto = simpleCommitteeDTOFactory.createWithDetails("Custom Committee", "Custom description")
        assertNoViolations(dto)
    }
}

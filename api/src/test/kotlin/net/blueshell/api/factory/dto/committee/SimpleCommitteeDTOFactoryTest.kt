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
}

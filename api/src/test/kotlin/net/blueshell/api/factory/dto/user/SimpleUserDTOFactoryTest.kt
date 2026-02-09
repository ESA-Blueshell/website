package net.blueshell.api.factory.dto.user

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import net.blueshell.api.shared.validation.group.Creation
import net.blueshell.api.shared.validation.group.Update
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SimpleUserDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var simpleUserDTOFactory: SimpleUserDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(simpleUserDTOFactory)
    }

    @Test
    fun `validates controller groups`() {
        val simpleUser = simpleUserDTOFactory.createBasic()
        assertNoViolations(simpleUser, Creation::class.java)
        assertNoViolations(simpleUser, Update::class.java)
    }
}

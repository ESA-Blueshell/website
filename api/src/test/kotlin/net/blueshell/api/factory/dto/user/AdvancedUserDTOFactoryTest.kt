package net.blueshell.api.factory.dto.user

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import net.blueshell.api.validation.group.Administration
import net.blueshell.api.validation.group.Creation
import net.blueshell.api.validation.group.Update
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AdvancedUserDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var advancedUserDTOFactory: AdvancedUserDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(advancedUserDTOFactory)
    }

    @Test
    fun `validates controller groups`() {
        val advancedUser = advancedUserDTOFactory.createBasic()
        assertNoViolations(advancedUser, Creation::class.java)
        assertNoViolations(advancedUser, Administration::class.java)
        assertNoViolations(advancedUser, Update::class.java)
    }
}

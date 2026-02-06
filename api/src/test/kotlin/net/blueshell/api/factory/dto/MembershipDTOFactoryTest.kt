package net.blueshell.api.factory.dto

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import net.blueshell.api.validation.group.Administration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MembershipDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var membershipDTOFactory: MembershipDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(membershipDTOFactory)
    }

    @Test
    fun `validates administration group`() {
        val membership = membershipDTOFactory.createBasic()
        assertNoViolations(membership, Administration::class.java)
    }
}

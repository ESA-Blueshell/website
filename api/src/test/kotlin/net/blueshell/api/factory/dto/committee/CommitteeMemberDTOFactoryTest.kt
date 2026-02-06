package net.blueshell.api.factory.dto.committee

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CommitteeMemberDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var committeeMemberDTOFactory: CommitteeMemberDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(committeeMemberDTOFactory)
    }

    @Test
    fun `creates role helpers`() {
        val chair = committeeMemberDTOFactory.createChair()
        assertEquals("Chair", chair.role)
        assertNoViolations(chair)
    }
}

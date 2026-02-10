package net.blueshell.api.factory.dto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BlogDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var blogDTOFactory: BlogDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(blogDTOFactory)
    }
}

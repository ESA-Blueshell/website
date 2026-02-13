package net.blueshell.api.factory.dto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PictureDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var pictureDTOFactory: PictureDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(pictureDTOFactory)
    }
}

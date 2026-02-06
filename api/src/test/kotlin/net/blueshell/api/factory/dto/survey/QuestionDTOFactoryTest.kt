package net.blueshell.api.factory.dto.survey

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class QuestionDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var questionDTOFactory: QuestionDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(questionDTOFactory)
    }
}

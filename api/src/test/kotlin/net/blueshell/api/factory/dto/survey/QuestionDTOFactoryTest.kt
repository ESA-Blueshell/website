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

    @Test
    fun `creates question by type`() {
        val open = questionDTOFactory.createByType(net.blueshell.api.shared.enums.QuestionType.OPEN)
        val radio = questionDTOFactory.createByType(net.blueshell.api.shared.enums.QuestionType.RADIO)
        val checkbox = questionDTOFactory.createByType(net.blueshell.api.shared.enums.QuestionType.CHECKBOX)
        val description = questionDTOFactory.createByType(net.blueshell.api.shared.enums.QuestionType.DESCRIPTION)
        assertNoViolations(open)
        assertNoViolations(radio)
        assertNoViolations(checkbox)
        assertNoViolations(description)
    }

    @Test
    fun `creates question helpers`() {
        val open = questionDTOFactory.createOpen()
        val radio = questionDTOFactory.createRadio()
        val checkbox = questionDTOFactory.createCheckbox()
        val description = questionDTOFactory.createDescription()
        assertNoViolations(open)
        assertNoViolations(radio)
        assertNoViolations(checkbox)
        assertNoViolations(description)
    }
}

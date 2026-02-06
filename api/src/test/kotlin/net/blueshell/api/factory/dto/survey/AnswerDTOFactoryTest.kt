package net.blueshell.api.factory.dto.survey

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AnswerDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var answerDTOFactory: AnswerDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(answerDTOFactory)
    }

    @Test
    fun `creates valid radio selections`() {
        val answer = answerDTOFactory.createForRadioQuestion(4, 2)
        assertEquals(1, answer.optionSelections?.count { it == true })
        assertEquals(4, answer.optionSelections?.size)
        assertNoViolations(answer)
    }

    @Test
    fun `creates valid checkbox selections`() {
        val answer = answerDTOFactory.createForCheckboxQuestion(5, listOf(1, 3))
        assertEquals(5, answer.optionSelections?.size)
        assertNoViolations(answer)
    }
}

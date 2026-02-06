package net.blueshell.api.factory.dto.survey

import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SurveyDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var surveyDTOFactory: SurveyDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(surveyDTOFactory)
    }

    @Test
    fun `assigns sequential indexes`() {
        val survey = surveyDTOFactory.createWithQuestionTypes(
            QuestionType.OPEN, QuestionType.RADIO, QuestionType.CHECKBOX
        )
        val indexes = survey.questions.mapNotNull { it.idx }
        assertEquals(listOf(1L, 2L, 3L), indexes)
        assertNoViolations(survey)
    }

    @Test
    fun `creates survey with open questions`() {
        val survey = surveyDTOFactory.createWithOpenQuestions(3)
        assertEquals(3, survey.questions.size)
        assertNoViolations(survey)
    }

    @Test
    fun `creates survey with mixed questions`() {
        val survey = surveyDTOFactory.createWithMixedQuestions()
        assertNoViolations(survey)
    }

    @Test
    fun `creates survey with multiple choice questions only`() {
        val survey = surveyDTOFactory.createWithMultipleChoiceOnly()
        assertNoViolations(survey)
    }
}

package net.blueshell.api.survey.web.dto

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.application.SurveyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SurveyDtoIT @Autowired constructor(
    private val surveyDTOFactory: SurveyDTOFactory,
    private val surveyService: SurveyService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists mapped survey questions`() {
            val dto = surveyDTOFactory.createWithQuestionTypes(QuestionType.OPEN, QuestionType.RADIO)
            val survey = dto.asEntity()
            val questions = survey.questions.toList()
            survey.questions.clear()

            val saved = surveyService.create(survey)
            entityManager.flush()

            questions.forEach { question ->
                question.survey = saved
                persist(question)
            }

            flushAndClear()

            val reloaded = reload(Survey::class.java, saved.id!!)

            assertThat(reloaded.questions).hasSize(dto.questions.size)
        }
    }
}

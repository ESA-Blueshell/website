package net.blueshell.api.survey.mapper

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.survey.mapper.SurveyMapper
import net.blueshell.api.survey.model.Survey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SurveyMapperIT @Autowired constructor(
    private val surveyMapper: SurveyMapper,
    private val surveyDTOFactory: SurveyDTOFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted survey`() {
            val survey = persist(surveyFactory.createBasic())
            entityManager.flush()
            persistQuestionWithSurvey(survey)
            persistQuestionWithSurvey(survey)
            flushAndClear()

            val reloaded = reload(Survey::class.java, survey.id!!)
            val dto = surveyMapper.toDTO(reloaded)

            assertThat(dto.id).isEqualTo(reloaded.id)
            assertThat(dto.questions).hasSize(2)
            assertThat(dto.responseCount).isEqualTo(reloaded.responseCount)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped survey questions`() {
            val dto = surveyDTOFactory.createWithQuestionTypes(QuestionType.OPEN, QuestionType.RADIO)
            val survey = surveyMapper.fromDTO(dto)
            val questions = survey.questions.toList()
            survey.questions.clear()

            val saved = persist(survey)
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

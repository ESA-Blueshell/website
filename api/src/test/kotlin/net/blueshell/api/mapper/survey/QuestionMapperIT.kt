package net.blueshell.api.mapper.survey

import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.survey.Question
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class QuestionMapperIT @Autowired constructor(
    private val questionMapper: QuestionMapper,
    private val questionDTOFactory: QuestionDTOFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted question`() {
            val survey = persistSurvey()
            val question = persist(questionFactory.createForSurvey(survey))

            val dto = questionMapper.toDTO(question)

            assertThat(dto.id).isEqualTo(question.id)
            assertThat(dto.label).isEqualTo(question.label)
            assertThat(dto.type).isEqualTo(question.type)
            assertThat(dto.idx).isEqualTo(question.idx)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists survey relation`() {
            val survey = persistSurvey()
            val dto = questionDTOFactory.createOpen().apply {
                surveyId = survey.id
            }
            val question = questionFactory.createForSurvey(survey)

            val mapped = questionMapper.fromDTO(dto, question)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Question::class.java, saved.id!!)

            assertThat(reloaded.surveyId).isEqualTo(survey.id)
            assertThat(reloaded.label).isEqualTo(dto.label)
        }
    }
}

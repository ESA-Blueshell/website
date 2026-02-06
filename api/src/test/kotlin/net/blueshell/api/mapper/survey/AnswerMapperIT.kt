package net.blueshell.api.mapper.survey

import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.survey.Answer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AnswerMapperIT @Autowired constructor(
    private val answerMapper: AnswerMapper,
    private val answerDTOFactory: AnswerDTOFactory,
    private val answerFactory: AnswerFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted answer`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = persist(answerFactory.createBasic().apply {
                this.question = question
            })

            val dto = answerMapper.toDTO(answer)

            assertThat(dto.id).isEqualTo(answer.id)
            assertThat(dto.questionId).isEqualTo(answer.questionId)
            assertThat(dto.optionSelections).isEqualTo(answer.optionSelections)
            assertThat(dto.textResponse).isEqualTo(answer.textResponse)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists question relation`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val dto = answerDTOFactory.createBasic().apply {
                questionId = question.id
            }
            val answer = answerFactory.createBasic().apply {
                this.question = question
            }

            val mapped = answerMapper.fromDTO(dto, answer)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Answer::class.java, saved.id!!)

            assertThat(reloaded.questionId).isEqualTo(question.id)
        }
    }
}

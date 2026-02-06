package net.blueshell.api.mapper.survey

import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.model.AnswerFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.survey.Answer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AnswerMapperIT @Autowired constructor(
    private val answerMapper: AnswerMapper,
    private val answerDTOFactory: AnswerDTOFactory,
    private val answerFactory: AnswerFactory
) : MapperTestSupport() {
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
        val mappedDto = answerMapper.toDTO(reloaded)

        assertThat(reloaded.questionId).isEqualTo(question.id)
        assertThat(mappedDto.questionId).isEqualTo(question.id)
    }
}

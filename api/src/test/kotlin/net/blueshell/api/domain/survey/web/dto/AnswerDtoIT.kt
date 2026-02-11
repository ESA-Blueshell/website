package net.blueshell.api.domain.survey.web.dto

import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.domain.survey.application.AnswerService
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.web.mapping.asEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AnswerDtoIT @Autowired constructor(
    private val answerDTOFactory: AnswerDTOFactory,
    private val answerFactory: AnswerFactory,
    private val answerService: AnswerService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
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

            val mapped = dto.asEntity(answer)
            val saved = answerService.create(mapped)
            flushAndClear()

            val reloaded = reload(Answer::class.java, saved.id!!)

            assertThat(reloaded.questionId).isEqualTo(question.id)
        }
    }
}

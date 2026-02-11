package net.blueshell.api.domain.survey.persistence

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.domain.survey.web.mapping.asDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AnswerModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = answerFactory.createBasic()
            answer.question = question
            answer.optionSelections = mutableListOf(true, false, true)
            answer.textResponse = "Response"

            val found = persistAndReload(answer, Answer::class.java) { it.id }

            assertEquals(answer.optionSelections, found.optionSelections)
            assertEquals(answer.textResponse, found.textResponse)
        }

        @Test
        fun `persists question relation when setting entity`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = answerFactory.createBasic()
            answer.question = question
            answer.optionSelections = mutableListOf(true, false, true)
            answer.textResponse = "Response"

            val found = persistAndReload(answer, Answer::class.java) { it.id }

            assertEquals(question.id, found.questionId)
            Assertions.assertEquals(question.id, found.question?.id)
        }

        @Test
        fun `persists question relation when setting id`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = answerFactory.createBasic()
            answer.question = question
            answer.optionSelections = mutableListOf(true, false, true)
            answer.textResponse = "Response"

            val found = persistAndReload(answer, Answer::class.java) { it.id }

            assertEquals(question.id, found.questionId)
            Assertions.assertEquals(question.id, found.question?.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted answer`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = persist(answerFactory.createBasic().apply {
                this.question = question
            })

            val dto = answer.asDto()

            assertEquals(answer.id, dto.id)
            assertEquals(answer.questionId, dto.questionId)
            assertEquals(answer.optionSelections, dto.optionSelections)
            assertEquals(answer.textResponse, dto.textResponse)
        }
    }
}

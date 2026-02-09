package net.blueshell.api.survey.domain.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.survey.domain.model.Answer
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
            answer.questionId = question.id!!
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
            assertEquals(question.id, found.question?.id)
        }

        @Test
        fun `persists question relation when setting id`() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = answerFactory.createBasic()
            answer.questionId = question.id!!
            answer.optionSelections = mutableListOf(true, false, true)
            answer.textResponse = "Response"

            val found = persistAndReload(answer, Answer::class.java) { it.id }

            assertEquals(question.id, found.questionId)
            assertEquals(question.id, found.question?.id)
        }
    }
}

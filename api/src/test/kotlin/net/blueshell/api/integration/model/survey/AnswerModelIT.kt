package net.blueshell.api.integration.model.survey

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.survey.Answer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AnswerModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_column_fields_and_question_relation() {
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = answerFactory.createBasic()
            answer.question = question
            answer.questionId = question.id ?: 0
            answer.optionSelections = mutableListOf(true, false, true)
            answer.textResponse = "Response"

            val found = persistAndReload(answer, Answer::class.java) { it.id }

            assertEquals(question.id, found.questionId)
            assertEquals(answer.optionSelections, found.optionSelections)
            assertEquals(answer.textResponse, found.textResponse)
        }
    }
}

package net.blueshell.api.survey.persistence

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.survey.persistence.Question
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QuestionModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val survey = persistSurvey()
            val question = questionFactory.createBasic()
            question.idx = 3
            question.surveyId = survey.id!!
            question.type = QuestionType.RADIO
            question.label = "Question?"
            question.choiceLabels = mutableListOf("A", "B", "C")

            val found = persistAndReload(question, Question::class.java) { it.id }

            assertEquals(question.idx, found.idx)
            assertEquals(question.type, found.type)
            assertEquals(question.label, found.label)
            assertEquals(question.choiceLabels, found.choiceLabels)
        }

        @Test
        fun `persists survey relation when setting entity`() {
            val survey = persistSurvey()
            val question = questionFactory.createBasic()
            question.idx = 3
            question.survey = survey
            question.type = QuestionType.RADIO
            question.label = "Question?"
            question.choiceLabels = mutableListOf("A", "B", "C")

            val found = persistAndReload(question, Question::class.java) { it.id }

            assertEquals(survey.id, found.surveyId)
            assertEquals(survey.id, found.survey.id)
        }

        @Test
        fun `persists survey relation when setting id`() {
            val survey = persistSurvey()
            val question = questionFactory.createBasic()
            question.idx = 3
            question.surveyId = survey.id!!
            question.type = QuestionType.RADIO
            question.label = "Question?"
            question.choiceLabels = mutableListOf("A", "B", "C")

            val found = persistAndReload(question, Question::class.java) { it.id }

            assertEquals(survey.id, found.surveyId)
            assertEquals(survey.id, found.survey.id)
        }
    }
}

package net.blueshell.api.integration.model.survey

import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.survey.Question
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QuestionModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_column_fields_and_survey_relation() {
            val survey = persistSurvey()
            val question = questionFactory.createBasic()
            question.idx = 3
            question.survey = survey
            question.surveyId = survey.id ?: 0
            question.type = QuestionType.RADIO
            question.label = "Question?"
            question.choiceLabels = mutableListOf("A", "B", "C")

            val found = persistAndReload(question, Question::class.java) { it.id }

            assertEquals(question.idx, found.idx)
            assertEquals(survey.id, found.surveyId)
            assertEquals(question.type, found.type)
            assertEquals(question.label, found.label)
            assertEquals(question.choiceLabels, found.choiceLabels)
        }
    }
}

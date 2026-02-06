package net.blueshell.api.model.survey

import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SurveyModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_questions_relation() {
            val survey = persistSurvey()
            val question = questionFactory.createBasic()
            question.type = QuestionType.OPEN
            question.label = "Survey question"
            question.survey = survey
            question.surveyId = survey.id ?: 0
            persist(question)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(Survey::class.java, survey.id)
            assertNotNull(found)

            assertEquals(1, found!!.questions.size)
            assertEquals("Survey question", found.questions.first().label)
        }
    }
}

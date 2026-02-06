package net.blueshell.api.integration.model.survey

import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.survey.Survey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SurveyModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists questions relation when setting entity`() {
            val survey = persistSurvey()
            val questionOne = questionFactory.createBasic()
            questionOne.type = QuestionType.OPEN
            questionOne.label = "Survey question 1"
            questionOne.survey = survey
            persist(questionOne)
            val questionTwo = questionFactory.createBasic()
            questionTwo.type = QuestionType.OPEN
            questionTwo.label = "Survey question 2"
            questionTwo.survey = survey
            persist(questionTwo)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(Survey::class.java, survey.id)
            assertNotNull(found)

            assertEquals(2, found!!.questions.size)
        }

        @Test
        fun `persists questions relation when setting id`() {
            val survey = persistSurvey()
            val questionOne = questionFactory.createBasic()
            questionOne.type = QuestionType.OPEN
            questionOne.label = "Survey question 1"
            questionOne.surveyId = survey.id ?: 0
            persist(questionOne)
            val questionTwo = questionFactory.createBasic()
            questionTwo.type = QuestionType.OPEN
            questionTwo.label = "Survey question 2"
            questionTwo.surveyId = survey.id ?: 0
            persist(questionTwo)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(Survey::class.java, survey.id)
            assertNotNull(found)

            assertEquals(2, found!!.questions.size)
        }
    }
}

package net.blueshell.api.survey.persistence

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.persistence.asDto
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
            questionOne.surveyId = survey.id!!
            persist(questionOne)
            val questionTwo = questionFactory.createBasic()
            questionTwo.type = QuestionType.OPEN
            questionTwo.label = "Survey question 2"
            questionTwo.surveyId = survey.id!!
            persist(questionTwo)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(Survey::class.java, survey.id)
            assertNotNull(found)

            assertEquals(2, found!!.questions.size)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted survey`() {
            val survey = persist(surveyFactory.createBasic())
            entityManager.flush()
            persistQuestionWithSurvey(survey)
            persistQuestionWithSurvey(survey)
            entityManager.flush()
            entityManager.clear()

            val reloaded = entityManager.find(Survey::class.java, survey.id)
            val dto = reloaded.asDto()

            assertEquals(reloaded.id, dto.id)
            assertEquals(2, dto.questions.size)
            assertEquals(reloaded.responseCount, dto.responseCount)
        }
    }
}

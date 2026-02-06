package net.blueshell.api.factory.model.survey

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.model.survey.Survey
import org.junit.jupiter.api.Test

class SurveyFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable survey`() {
        val survey = surveyFactory.createBasic()
        val saved = persist(survey)
        assertPersisted(Survey::class.java, saved.id)
    }

    @Test
    fun `creates persistable survey with questions`() {
        val survey = surveyFactory.createWithQuestions(2)
        val questions = survey.questions.toList()
        survey.questions.clear()

        val saved = persist(survey)
        entityManager.flush()

        questions.forEach { question ->
            question.survey = saved
            question.surveyId = saved.id ?: 0L
            persist(question)
        }

        assertPersisted(Survey::class.java, saved.id)
    }
}
